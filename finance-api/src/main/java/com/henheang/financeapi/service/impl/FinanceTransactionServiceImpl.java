package com.henheang.financeapi.service.impl;

import com.henheang.financeapi.config.FinanceTime;
import com.henheang.financeapi.domain.FinanceCategory;
import com.henheang.financeapi.domain.FinancePaymentMethod;
import com.henheang.financeapi.domain.FinanceTransaction;
import com.henheang.financeapi.domain.money.KrwConverter;
import com.henheang.financeapi.domain.money.KrwConverter.ResolvedAmounts;
import com.henheang.financeapi.payload.transaction.BulkTransactionRequest;
import com.henheang.financeapi.payload.transaction.BulkTransactionResponse;
import com.henheang.financeapi.payload.transaction.DescriptionSuggestionResponse;
import com.henheang.financeapi.payload.transaction.TransactionListQuery;
import com.henheang.financeapi.payload.transaction.TransactionPageResponse;
import com.henheang.financeapi.payload.transaction.TransactionRequest;
import com.henheang.financeapi.payload.transaction.TransactionResponse;
import com.henheang.financeapi.payload.transaction.TransactionValues;
import com.henheang.financeapi.repository.FinanceCategoryRepository;
import com.henheang.financeapi.repository.FinancePaymentMethodRepository;
import com.henheang.financeapi.repository.FinanceTransactionRepository;
import com.henheang.financeapi.repository.TransactionFilter;
import com.henheang.financeapi.repository.TransactionSort;
import com.henheang.financeapi.repository.TransactionSuggestionRepository;
import com.henheang.financeapi.service.FinanceTransactionService;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Transactions, ported from Luyra's finance-transaction-service. Beyond Luyra:
 *
 * <ul>
 *   <li>a referenced category or payment method must be the caller's own (404 otherwise) - Luyra
 *       accepted any existing id, and the composite foreign keys back this up in the database;
 *   <li>the category's type must fit the transaction (an expense-only category can't hold income) -
 *       Luyra checked this only in bulk edits and the UI;
 *   <li>KRW must be whole won and amounts are BigDecimal end to end (KrwConverter, D-7).
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FinanceTransactionServiceImpl implements FinanceTransactionService {

    private static final String BOTH = "both";

    // Luyra's SUGGESTION_LIMIT, 365-day window and q max length.
    private static final int SUGGESTION_LIMIT = 6;
    private static final int SUGGESTION_WINDOW_DAYS = 365;
    private static final int SUGGESTION_QUERY_MAX = 100;

    private final FinanceTransactionRepository transactionRepository;
    private final FinanceCategoryRepository categoryRepository;
    private final FinancePaymentMethodRepository paymentMethodRepository;
    private final TransactionSuggestionRepository suggestionRepository;
    private final FinanceTime financeTime;

    @Override
    @Transactional(readOnly = true)
    public TransactionPageResponse list(UUID userId, TransactionListQuery query) {
        TransactionFilter filter = toFilter(query);
        int page = query.getPage() == null ? 0 : Integer.parseInt(query.getPage());
        int pageSize = TransactionValues.PAGE_SIZE;

        List<FinanceTransaction> rows =
                transactionRepository.search(userId, filter, page * pageSize, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<FinanceTransaction> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

        Map<UUID, FinanceCategory> categories =
                byId(
                        ids(pageRows, FinanceTransaction::getCategoryId),
                        ids -> categoryRepository.findAllByUserIdAndIdIn(userId, ids),
                        FinanceCategory::getId);
        Map<UUID, FinancePaymentMethod> paymentMethods =
                byId(
                        ids(pageRows, FinanceTransaction::getPaymentMethodId),
                        ids -> paymentMethodRepository.findAllByUserIdAndIdIn(userId, ids),
                        FinancePaymentMethod::getId);

        List<TransactionResponse> transactions =
                pageRows.stream()
                        .map(
                                t ->
                                        TransactionResponse.from(
                                                t,
                                                lookup(categories, t.getCategoryId()),
                                                lookup(paymentMethods, t.getPaymentMethodId())))
                        .toList();
        return new TransactionPageResponse(transactions, hasMore);
    }

    @Override
    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest request) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUserId(userId);
        return write(userId, transaction, request);
    }

    @Override
    @Transactional
    public TransactionResponse update(UUID userId, UUID id, TransactionRequest request) {
        return write(userId, findOwned(userId, id), request);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        transactionRepository.delete(findOwned(userId, id));
    }

    // Luyra's applyBulkTransactionAction, in one transaction. Ids that aren't
    // the caller's (or no longer exist) are skipped, and only the rows that
    // changed count. Recategorize also checks the category is the caller's
    // and fits every selected row, so a bulk edit can't produce a row the
    // single edit would refuse.
    @Override
    @Transactional
    public BulkTransactionResponse bulk(UUID userId, BulkTransactionRequest request) {
        List<UUID> ids = request.getIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BadRequestException("Duplicate transaction");
        }
        if (BulkTransactionRequest.DELETE.equals(request.getAction())) {
            return new BulkTransactionResponse(transactionRepository.deleteAllForUser(userId, ids));
        }

        // categoryId is a three-state Optional: absent (null) is an error,
        // an explicit JSON null (empty) clears the category. Comparing it to
        // null is intentional - don't "fix" it to isEmpty().
        if (request.getCategoryId() == null) {
            throw new BadRequestException("categoryId is required to recategorize");
        }
        UUID categoryId = request.getCategoryId().orElse(null);
        FinanceCategory category = ownedCategory(userId, categoryId);
        List<FinanceTransaction> rows = transactionRepository.lockAllForUser(userId, ids);
        // category is null when clearing, so there is no type to check.
        if (category != null && !BOTH.equals(category.getType())) {
            boolean mismatch = rows.stream().anyMatch(t -> !category.getType().equals(t.getType()));
            if (mismatch) {
                throw new BadRequestException(
                        category.getName()
                                + " is an "
                                + category.getType()
                                + " category, but the selection includes "
                                + ("expense".equals(category.getType()) ? "income." : "expenses."));
            }
        }
        rows.forEach(t -> t.setCategoryId(categoryId));
        transactionRepository.flush();
        return new BulkTransactionResponse(rows.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DescriptionSuggestionResponse> suggest(UUID userId, String query, String type) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            throw new BadRequestException("q is required");
        }
        if (q.length() > SUGGESTION_QUERY_MAX) {
            throw new BadRequestException("q must be at most 100 characters");
        }
        if (type != null && !type.matches(TransactionValues.TYPES)) {
            throw new BadRequestException(TransactionValues.TYPE_MESSAGE);
        }
        LocalDate since = financeTime.today().minusDays(SUGGESTION_WINDOW_DAYS);
        return suggestionRepository.suggest(userId, q, type, since, SUGGESTION_LIMIT);
    }

    // Create and update share every rule: update is a full replace.
    private TransactionResponse write(
            UUID userId, FinanceTransaction transaction, TransactionRequest request) {
        ResolvedAmounts amounts =
                KrwConverter.resolve(
                        request.getCurrency(),
                        request.getAmountKrw(),
                        request.getOriginalAmount(),
                        request.getExchangeRate());
        FinanceCategory category = ownedCategory(userId, request.getCategoryId());
        requireCompatibleCategory(category, request.getType());
        FinancePaymentMethod paymentMethod =
                ownedPaymentMethod(userId, request.getPaymentMethodId());

        transaction.setDate(request.getDate());
        transaction.setType(request.getType());
        transaction.setCategoryId(request.getCategoryId());
        transaction.setDescription(request.getDescription());
        transaction.setCurrency(amounts.currency());
        transaction.setAmountKrw(amounts.amountKrw());
        transaction.setAmountUsd(amounts.amountUsd());
        transaction.setExchangeRate(amounts.exchangeRate());
        transaction.setPaymentMethodId(request.getPaymentMethodId());
        transaction.setNote(request.getNote());

        return TransactionResponse.from(
                transactionRepository.saveAndFlush(transaction), category, paymentMethod);
    }

    private FinanceTransaction findOwned(UUID userId, UUID id) {
        return transactionRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));
    }

    private FinanceCategory ownedCategory(UUID userId, UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository
                .findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
    }

    private FinancePaymentMethod ownedPaymentMethod(UUID userId, UUID paymentMethodId) {
        if (paymentMethodId == null) {
            return null;
        }
        return paymentMethodRepository
                .findByIdAndUserId(paymentMethodId, userId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Payment method", "id", paymentMethodId));
    }

    static void requireCompatibleCategory(FinanceCategory category, String transactionType) {
        if (category == null
                || BOTH.equals(category.getType())
                || category.getType().equals(transactionType)) {
            return;
        }
        throw new BadRequestException(
                category.getName()
                        + " is an "
                        + category.getType()
                        + " category, so it can't be used for "
                        + ("income".equals(transactionType) ? "income." : "an expense."));
    }

    private static TransactionFilter toFilter(TransactionListQuery query) {
        String search = StringUtils.hasText(query.getSearch()) ? query.getSearch().trim() : null;
        if (search != null && search.length() > TransactionValues.SEARCH_MAX) {
            throw new BadRequestException("search must be at most 200 characters");
        }
        TransactionSort sort =
                query.getSort() == null
                        ? TransactionSort.DATE_DESC
                        : TransactionSort.fromValue(query.getSort())
                                .orElseThrow(
                                        () ->
                                                new BadRequestException(
                                                        TransactionValues.SORT_MESSAGE));
        return new TransactionFilter(
                date(query.getStart(), "start"),
                date(query.getEnd(), "end"),
                query.getType(),
                query.getCategoryId() == null ? null : UUID.fromString(query.getCategoryId()),
                query.getPaymentMethodId() == null
                        ? null
                        : UUID.fromString(query.getPaymentMethodId()),
                query.getAmountMin() == null ? null : new BigDecimal(query.getAmountMin()),
                query.getAmountMax() == null ? null : new BigDecimal(query.getAmountMax()),
                search,
                sort);
    }

    // The pattern guarantees the shape; this rejects impossible days (2026-02-30).
    private static LocalDate date(String value, String name) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(name + " must be a valid date (YYYY-MM-DD)");
        }
    }

    private static List<UUID> ids(
            List<FinanceTransaction> rows, Function<FinanceTransaction, UUID> id) {
        return rows.stream().map(id).filter(Objects::nonNull).distinct().toList();
    }

    // Map.of() and Collectors.toMap() results reject get(null); an
    // uncategorised transaction has a null id.
    private static <T> T lookup(Map<UUID, T> byId, UUID id) {
        return id == null ? null : byId.get(id);
    }

    private static <T> Map<UUID, T> byId(
            List<UUID> ids, Function<List<UUID>, List<T>> load, Function<T, UUID> id) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return load.apply(ids).stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
