package com.chayitong.chayitong_backend;

public class UserFavoritesResponse {
  private final UserResponse user;
  private final TeaPageResponse favorites;

  public UserFavoritesResponse(UserResponse user, TeaPageResponse favorites) {
    this.user = user;
    this.favorites = favorites;
  }

  public UserResponse getUser() {
    return user;
  }

  public TeaPageResponse getFavorites() {
    return favorites;
  }
}
