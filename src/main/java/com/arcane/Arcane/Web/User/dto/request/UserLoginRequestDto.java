package com.arcane.Arcane.Web.User.dto.request;

import lombok.Data;

@Data
public class UserLoginRequestDto {
    private String loginId;
    private String loginPw;
}
