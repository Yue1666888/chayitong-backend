package com.chayitong.chayitong_backend;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UserController {
  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/api/users/{userId}/favorites")
  public UserFavoritesResponse getFavorites(
      @PathVariable("userId") String userId,
      @Valid @ModelAttribute PageRequest pagination) {
    UserFavoritesResponse response = userService.findFavorites(
        userId, pagination.getPage(), pagination.getPageSize());
    if (response == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
    }
    return response;
  }

  @PutMapping("/api/users/{userId}/favorites/{teaId}")
  public ResponseEntity<Void> addFavorite(
      @PathVariable("userId") String userId,
      @PathVariable("teaId") String teaId) {
    FavoriteCreationResult result = userService.addFavorite(userId, teaId);
    if (result == FavoriteCreationResult.RESOURCE_NOT_FOUND) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户或茶叶不存在");
    }
    if (result == FavoriteCreationResult.CREATED) {
      return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/users/{userId}/favorites/{teaId}")
  public ResponseEntity<Void> removeFavorite(
      @PathVariable("userId") String userId,
      @PathVariable("teaId") String teaId) {
    if (!userService.removeFavorite(userId, teaId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
    }
    return ResponseEntity.noContent().build();
  }
}
