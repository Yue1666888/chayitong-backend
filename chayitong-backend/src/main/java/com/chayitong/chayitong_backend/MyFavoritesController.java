package com.chayitong.chayitong_backend;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MyFavoritesController {
  private final UserService userService;

  public MyFavoritesController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/api/me/favorites")
  public UserFavoritesResponse getFavorites(
      @AuthenticationPrincipal LoginUser currentUser,
      @Valid @ModelAttribute TeaPageRequest pagination) {
    // 用户编号来自已经验证的登录身份，调用方只需要传分页参数。
    UserFavoritesResponse response = userService.findFavorites(
        currentUser.getUserId(), pagination.getPage(), pagination.getPageSize());
    if (response == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
    }
    return response;
  }

  @PutMapping("/api/me/favorites/{teaId}")
  public ResponseEntity<Void> addFavorite(
      @AuthenticationPrincipal LoginUser currentUser,
      @PathVariable("teaId") String teaId) {
    FavoriteCreationResult result = userService.addFavorite(currentUser.getUserId(), teaId);
    if (result == FavoriteCreationResult.RESOURCE_NOT_FOUND) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户或茶叶不存在");
    }
    if (result == FavoriteCreationResult.CREATED) {
      return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/me/favorites/{teaId}")
  public ResponseEntity<Void> removeFavorite(
      @AuthenticationPrincipal LoginUser currentUser,
      @PathVariable("teaId") String teaId) {
    if (!userService.removeFavorite(currentUser.getUserId(), teaId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
    }
    return ResponseEntity.noContent().build();
  }
}
