package com.henheang.engineeringapi.service.impl;

import com.henheang.engineeringapi.domain.Project;
import com.henheang.engineeringapi.exception.ConflictException;
import com.henheang.engineeringapi.exception.InvalidRequestException;
import com.henheang.engineeringapi.exception.NotFoundException;
import com.henheang.engineeringapi.payload.ProjectRequest;
import com.henheang.engineeringapi.payload.ProjectResponse;
import com.henheang.engineeringapi.repository.ProjectRepository;
import com.henheang.engineeringapi.service.ProjectMapper;
import com.henheang.engineeringapi.service.ProjectService;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    /** A personal registry; the cap only bounds a runaway script. */
    static final int MAX_PROJECTS_PER_OWNER = 100;

    private final ProjectRepository projects;
    private final ProjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID ownerId) {
        return projects.listForUser(ownerId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse get(UUID ownerId, UUID projectId) {
        return mapper.toResponse(load(ownerId, projectId));
    }

    @Override
    @Transactional
    public ProjectResponse create(UUID ownerId, ProjectRequest request) {
        if (request.version() != null) {
            throw new InvalidRequestException("version must be absent when creating a project.");
        }
        checkRules(request);
        if (projects.countByUserId(ownerId) >= MAX_PROJECTS_PER_OWNER) {
            throw new ConflictException(
                    "The registry is full (" + MAX_PROJECTS_PER_OWNER + " projects).");
        }
        if (projects.existsByUserIdAndSlug(ownerId, request.slug())) {
            throw duplicateSlug(request.slug());
        }
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setUserId(ownerId);
        mapper.copyEditableFields(request, project);
        return mapper.toResponse(projects.saveAndFlush(project));
    }

    @Override
    @Transactional
    public ProjectResponse replace(UUID ownerId, UUID projectId, ProjectRequest request) {
        if (request.version() == null) {
            throw new InvalidRequestException(
                    "version is required: send the version you read, so a newer change isn't"
                            + " overwritten.");
        }
        checkRules(request);
        Project project = load(ownerId, projectId);
        if (!Objects.equals(project.getVersion(), request.version())) {
            throw changedElsewhere();
        }
        if (projects.existsByUserIdAndSlugAndIdNot(ownerId, request.slug(), projectId)) {
            throw duplicateSlug(request.slug());
        }
        mapper.copyEditableFields(request, project);
        return mapper.toResponse(projects.saveAndFlush(project));
    }

    @Override
    @Transactional
    public void delete(UUID ownerId, UUID projectId) {
        projects.delete(load(ownerId, projectId));
    }

    private Project load(UUID ownerId, UUID projectId) {
        return projects.findByIdAndUserId(projectId, ownerId)
                .orElseThrow(() -> new NotFoundException("Project"));
    }

    /** Rules bean validation can't express on its own. */
    private static void checkRules(ProjectRequest request) {
        if ((request.monthlyCostCents() == null) != (request.costCurrency() == null)) {
            throw new InvalidRequestException(
                    "monthlyCostCents and costCurrency go together: send both or neither.");
        }
        if (request.productionUrl() != null && !isPublicHttpsUrl(request.productionUrl())) {
            throw new InvalidRequestException(
                    "productionUrl must be an https:// URL with a dotted host name and no"
                            + " credentials.");
        }
    }

    // A link the owner will open later: https, a real dotted host, and no
    // user:password@ part that would end up stored and shown.
    private static boolean isPublicHttpsUrl(String value) {
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getHost().contains(".")
                    && uri.getRawUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static ConflictException duplicateSlug(String slug) {
        return new ConflictException("You already have a project with the slug '" + slug + "'.");
    }

    static ConflictException changedElsewhere() {
        return new ConflictException(
                "This project was changed elsewhere. Reload it and try again.");
    }
}
