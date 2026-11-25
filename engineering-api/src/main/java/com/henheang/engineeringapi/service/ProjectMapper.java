package com.henheang.engineeringapi.service;

import com.henheang.engineeringapi.domain.Project;
import com.henheang.engineeringapi.payload.ProjectRequest;
import com.henheang.engineeringapi.payload.ProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Project DTO mapping. Identity and bookkeeping fields - id, owner, timestamps, version - are never
 * taken from a request; an unmapped target fails the build ({@code unmappedTargetPolicy = ERROR}),
 * so a new entity field can't be filled from the body by accident.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void copyEditableFields(ProjectRequest request, @MappingTarget Project project);
}
