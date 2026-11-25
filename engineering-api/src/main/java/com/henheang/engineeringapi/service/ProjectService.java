package com.henheang.engineeringapi.service;

import com.henheang.engineeringapi.payload.ProjectRequest;
import com.henheang.engineeringapi.payload.ProjectResponse;
import java.util.List;
import java.util.UUID;

/**
 * The project registry. {@code ownerId} always comes from the verified token, never from a request;
 * another owner's project is "not found".
 */
public interface ProjectService {

    List<ProjectResponse> list(UUID ownerId);

    ProjectResponse get(UUID ownerId, UUID projectId);

    ProjectResponse create(UUID ownerId, ProjectRequest request);

    /** Replaces the editable fields; {@code request.version()} must match the stored version. */
    ProjectResponse replace(UUID ownerId, UUID projectId, ProjectRequest request);

    void delete(UUID ownerId, UUID projectId);
}
