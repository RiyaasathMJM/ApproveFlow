package com.neirah.approveflow.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.neirah.approveflow.user.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;


public interface RequestRepository extends JpaRepository<Request, Long> {
    Page<Request> findByOwnerId(Long ownerId, Pageable pageable);
    Page<Request> findByOwnerIdAndStatus(Long ownerId, RequestStatus status, Pageable pageable);
    Page<Request> findByStatus(RequestStatus status, Pageable pageable);
    @Query("""
        select r from Request r
        where r.status = com.neirah.approveflow.request.RequestStatus.PENDING_APPROVAL
          and r.owner.id <> :userId
          and r.currentStepId in (select s.id from WorkflowStep s where s.approverRole in :roles)
        """)
    Page<Request> findPendingFor(@Param("userId") Long userId,
                                 @Param("roles") Collection<Role> roles, Pageable pageable);
}