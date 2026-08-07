package az.catalog.modules.user;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import az.catalog.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · İstifadəçilər", description = "Yalnız SUPER_ADMIN")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Admin siyahısı")
    @GetMapping
    public PageResponse<UserDtos.UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(userService.search(search, role, status, pageable));
    }

    @Operation(summary = "Admin detalı")
    @GetMapping("/{id}")
    public DataResponse<UserDtos.UserResponse> get(@PathVariable Long id) {
        return DataResponse.of(userService.get(id));
    }

    @Operation(summary = "Admin yaratmaq")
    @PostMapping
    public DataResponse<UserDtos.UserResponse> create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return DataResponse.of(userService.create(request));
    }

    @Operation(summary = "Admin məlumatını dəyişmək")
    @PutMapping("/{id}")
    public DataResponse<UserDtos.UserResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody UserDtos.UpdateRequest request) {
        return DataResponse.of(userService.update(id, request));
    }

    @Operation(summary = "Status dəyişmək")
    @PatchMapping("/{id}/status")
    public DataResponse<UserDtos.UserResponse> changeStatus(@PathVariable Long id,
                                                            @Valid @RequestBody UserDtos.StatusRequest request) {
        return DataResponse.of(userService.changeStatus(id, request.status(), SecurityUtils.requireAdmin().id()));
    }

    @Operation(summary = "Rol dəyişmək")
    @PatchMapping("/{id}/role")
    public DataResponse<UserDtos.UserResponse> changeRole(@PathVariable Long id,
                                                          @Valid @RequestBody UserDtos.RoleRequest request) {
        return DataResponse.of(userService.changeRole(id, request.role(), SecurityUtils.requireAdmin().id()));
    }

    @Operation(summary = "Şifrə təyin etmək")
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> setPassword(@PathVariable Long id,
                                            @Valid @RequestBody UserDtos.SetPasswordRequest request) {
        userService.setPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Admini silmək (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id, SecurityUtils.requireAdmin().id());
        return ResponseEntity.noContent().build();
    }
}
