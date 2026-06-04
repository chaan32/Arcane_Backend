package com.arcane.Arcane.Web.PatchNote.repository;

import com.arcane.Arcane.Web.PatchNote.domain.PatchNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchNoteRepository extends JpaRepository<PatchNote, Long> {
}
