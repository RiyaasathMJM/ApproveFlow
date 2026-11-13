package com.neirah.approveflow.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.neirah.approveflow.user.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;


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

    @Query("""
        select r.id from Request r
        where r.status = com.neirah.approveflow.request.RequestStatus.PENDING_APPROVAL
          and r.currentStepDueAt < :now
        """)
    List<Long> findOverdueIds(@Param("now") Instant now);

    @Query("""
        select count(r) from Request r
        where r.status = com.neirah.approveflow.request.RequestStatus.PENDING_APPROVAL
          and r.currentStepDueAt < :now
        """)
    long countOverdue(@Param("now") Instant now);

    long countByStatus(RequestStatus status);

    @Query(value = """
        SELECT AVG(TIMESTAMPDIFF(SECOND, submitted_at, completed_at))
        FROM requests
        WHERE status = 'APPROVED' AND completed_at IS NOT NULL AND submitted_at IS NOT NULL
        """, nativeQuery = true)
    Number averageApprovalSeconds();

    @Query(value = "SELECT * FROM requests WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Request> findByIdForUpdate(@Param("id") Long id);
}