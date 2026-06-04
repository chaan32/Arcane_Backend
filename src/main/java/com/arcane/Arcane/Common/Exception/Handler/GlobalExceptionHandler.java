package com.arcane.Arcane.Common.Exception.Handler;

import com.arcane.Arcane.Common.Exception.Fail.*;
import com.arcane.Arcane.Common.Exception.Normal.CannotFoundChampion;
import com.arcane.Arcane.Common.Exception.Normal.CannotFoundComment;
import com.arcane.Arcane.Common.Exception.Normal.CannotFoundPatchNote;
import com.arcane.Arcane.Common.Exception.RiotAPI.CannotFoundSummoner;
import com.arcane.Arcane.Common.Exception.Normal.CannotFoundUser;
import com.arcane.Arcane.Common.Exception.RiotAPI.IsPresentLoginId;
import com.arcane.Arcane.Common.Exception.RiotAPI.RiotInternalError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IsPresentLoginId.class)
    public ResponseEntity<?> handleIsPresentLoginId(IsPresentLoginId e) {
        Map<String, Object> error = new HashMap<>();
        error.put("enroll", "fail");
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(CannotFoundSummoner.class)
    public ResponseEntity<?> handleCannotFindSummoner(CannotFoundSummoner e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(CannotFoundUser.class)
    public ResponseEntity<?> handleCannotFindUser(CannotFoundUser e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(CannotFoundPatchNote.class)
    public ResponseEntity<?> handleCannotFindPatchNote(CannotFoundPatchNote e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(CannotFoundComment.class)
    public ResponseEntity<?> handleCannotFindComment(CannotFoundComment e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(EditFail.class)
    public ResponseEntity<?> editFailComment(EditFail e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(DeleteFail.class)
    public ResponseEntity<?> deleteFailComment(DeleteFail e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(SignUpFail.class)
    public ResponseEntity<?> signUpFail(SignUpFail e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(error);
    }

    @ExceptionHandler(CannotSignUp.class)
    public ResponseEntity<?> cannotSignUp(CannotSignUp e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(error);
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<?> sqlException(SQLException e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> illegalArgument(IllegalArgumentException e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> illegalState(IllegalStateException e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(CannotFoundChampion.class)
    public ResponseEntity<?> cannotFoundChampion(CannotFoundChampion e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(PositionError.class)
    public ResponseEntity<?> positionError(PositionError e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(RiotInternalError.class)
    public ResponseEntity<?> riotInternalError(RiotInternalError e) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(TooManyRequestFail.class)
    public ResponseEntity apiRequestLimit(TooManyRequestFail e){
        Map<String, Object> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

}
