package com.henheang.financeapi.repository;

import com.henheang.financeapi.payload.transaction.DescriptionSuggestionResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Description autocomplete, ported from Luyra's findDescriptionSuggestions as explicit SQL - an
 * aggregate with array_agg and a computed ranking that JPQL can't express. Scoped by user_id first.
 *
 * <p>Ranking: uses x 3 when the description starts with the query, else x 1; then the most recent
 * use; then the description (a stable tie-break Luyra lacks). type, category and payment method
 * come from the latest use. Only uses on or after {@code since} count - the caller passes a Seoul
 * date, where Luyra used the database session's current_date.
 */
@Repository
@RequiredArgsConstructor
public class TransactionSuggestionRepository {

    private static final String SUGGESTIONS =
            "SELECT description,"
                    + " (array_agg(type ORDER BY date DESC, created_at DESC))[1] AS type,"
                    + " (array_agg(category_id ORDER BY date DESC, created_at DESC))[1]"
                    + " AS category_id,"
                    + " (array_agg(payment_method_id ORDER BY date DESC, created_at DESC))[1]"
                    + " AS payment_method_id,"
                    + " count(*)::int AS uses"
                    + " FROM finance_transactions"
                    + " WHERE user_id = :userId"
                    + " AND description ILIKE :contains ESCAPE '\\'"
                    + " AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text))"
                    + " AND date >= :since"
                    + " GROUP BY description"
                    + " ORDER BY count(*) * (CASE WHEN bool_or(description ILIKE :prefix"
                    + " ESCAPE '\\') THEN 3 ELSE 1 END) DESC,"
                    + " max(date) DESC, description ASC"
                    + " LIMIT :limit";

    private final NamedParameterJdbcTemplate jdbc;

    public List<DescriptionSuggestionResponse> suggest(
            UUID userId, String query, String type, LocalDate since, int limit) {
        String escaped = escapeLike(query);
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("contains", "%" + escaped + "%")
                        .addValue("prefix", escaped + "%")
                        .addValue("type", type)
                        .addValue("since", since)
                        .addValue("limit", limit);
        return jdbc.query(
                SUGGESTIONS,
                params,
                (rs, row) ->
                        new DescriptionSuggestionResponse(
                                rs.getString("description"),
                                rs.getString("type"),
                                rs.getObject("category_id", UUID.class),
                                rs.getObject("payment_method_id", UUID.class),
                                rs.getInt("uses")));
    }

    // Same escaping as Luyra: backslash, % and _ match themselves.
    static String escapeLike(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == '\\' || c == '%' || c == '_') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
