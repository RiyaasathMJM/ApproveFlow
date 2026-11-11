package com.neirah.approveflow.request;

import com.neirah.approveflow.common.PageResponse;
import com.neirah.approveflow.request.RequestDtos.*;
import com.neirah.approveflow.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService service;

    public RequestController(RequestService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('REQUESTER')")
    public RequestResponse create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateRequest body) {
        return service.create(user, body);
    }

    @PatchMapping("/{id}")
    public RequestResponse update(@PathVariable Long id, @AuthenticationPrincipal User user,
                                  @Valid @RequestBody UpdateRequest body) {
        return service.update(id, user, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        service.delete(id, user);
    }

    @PostMapping("/{id}/submit")
    public RequestResponse submit(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.submit(id, user);
    }

    @GetMapping
    public PageResponse<RequestResponse> list(@AuthenticationPrincipal User user,
                                              @RequestParam(required = false) RequestStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(user, status, page, size));
    }

    @GetMapping("/{id}")
    public RequestDetailResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.get(id, user);
    }
}