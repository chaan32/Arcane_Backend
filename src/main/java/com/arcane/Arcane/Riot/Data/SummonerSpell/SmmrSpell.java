package com.arcane.Arcane.Riot.Data.SummonerSpell;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmmrSpell {

    @Id
    private Long spellId; // 4 = 점멸

    private String name;  // 점멸

    @Column(length = 1000)
    private String description;

    private String imageFull; // SummonerFlash.png
}
