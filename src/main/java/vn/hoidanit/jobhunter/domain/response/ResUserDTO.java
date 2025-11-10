package vn.hoidanit.jobhunter.domain.response;

import java.time.Instant;
import java.util.List;

import vn.hoidanit.jobhunter.domain.Permission;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.util.constant.GenderEnum;
import vn.hoidanit.jobhunter.util.constant.LevelEnum;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResUserDTO {
    private long id;
    private String email;
    private String name;
    private GenderEnum gender;
    private String address;
    private String phone;
    private int age;
    private double salary;
    private LevelEnum level;
    private Instant updatedAt;
    private Instant createdAt;

    private CompanyUser company;

    private RoleUser role;
    private List<Skill> skills;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CompanyUser {
        private long id;
        private String name;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoleUser {
        private long id;
        private String name;
        private List<Permission> permissions;
    }
}
