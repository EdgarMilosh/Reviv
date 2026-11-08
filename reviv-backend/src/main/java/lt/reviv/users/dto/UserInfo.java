package lt.reviv.users.dto;

import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;

public record UserInfo(Long id, String email, String name, UserRole role) {

    public static UserInfo from(AppUser user) {
        return new UserInfo(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}