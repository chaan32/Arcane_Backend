package com.arcane.Arcane.Web.User.dto.request;

import lombok.Data;

@Data
public class UserCheckGameNameRequestDto {
    private String gameName;
    private String tagLine;
}
