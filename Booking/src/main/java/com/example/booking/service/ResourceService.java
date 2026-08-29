package com.example.booking.service;

import com.example.booking.dto.ResourceRequest;
import com.example.booking.dto.ResourceResponse;
import com.example.booking.entity.Resource;
import com.example.booking.exception.ResourceNotFoundException;
import com.example.booking.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;

    @Transactional(readOnly = true)
    public List<ResourceResponse> getAllResources() {
        return resourceRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse getResourceById(Long id) {
        return toResponse(findResource(id));
    }

    @Transactional
    public ResourceResponse createResource(ResourceRequest request) {
        Resource resource = Resource.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .available(request.getAvailable())
                .price(request.getPrice())
                .build();

        Resource saved = resourceRepository.save(resource);
        return toResponse(saved);
    }

    @Transactional
    public ResourceResponse updateResource(Long id, ResourceRequest request) {
        Resource resource = findResource(id);

        resource.setName(request.getName());
        resource.setDescription(request.getDescription());
        resource.setType(request.getType());
        resource.setAvailable(request.getAvailable());
        resource.setPrice(request.getPrice());

        // No save() call — @Transactional dirty checking flushes changes automatically ⭐
        return toResponse(resource);
    }

    @Transactional
    public void deleteResource(Long id) {
        Resource resource = findResource(id);
        resourceRepository.delete(resource);
    }

    // ─── helpers ───────────────────────────────────────────────

    private Resource findResource(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resource not found with id: " + id));
    }

    private ResourceResponse toResponse(Resource r) {
        return ResourceResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .description(r.getDescription())
                .type(r.getType())
                .available(r.isAvailable())
                .price(r.getPrice())
                .build();
    }
}