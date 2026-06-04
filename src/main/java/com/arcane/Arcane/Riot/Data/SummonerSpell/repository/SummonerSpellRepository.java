package com.arcane.Arcane.Riot.Data.SummonerSpell.repository;

import com.arcane.Arcane.Riot.Data.SummonerSpell.SmmrSpell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SummonerSpellRepository extends JpaRepository<SmmrSpell, Long> {
    SmmrSpell getSmmrSpellBySpellId(Long id);
}
