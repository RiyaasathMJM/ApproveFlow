package com.neirah.approveflow.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, Long> {
    Page<Request> findByOwnerId(Long ownerId, Pageable pageable);
    Page<Request> findByOwnerIdAndStatus(Long ownerId, RequestStatus status, Pageable pageable);
    Page<Request> findByStatus(RequestStatus status, Pageable pageable);
}