package com.arcane.Arcane.Web.Guide.repository;

import com.arcane.Arcane.Riot.Data.Champion.Champion;
import org.springframework.data.jpa.repository.JpaRepository;
import com.arcane.Arcane.Web.Guide.domain.Guide;

import java.util.List;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    List<Guide> findByChampion(Champion champion);
    List<Guide> findAllByOrderByUpdatedAtDesc();
}
