package com.chayitong.chayitong_backend;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository userRepository;
  private final TeaFavoriteRepository teaFavoriteRepository;
  private final TeaRepository teaRepository;

  public UserService(UserRepository userRepository, TeaFavoriteRepository teaFavoriteRepository,
      TeaRepository teaRepository) {
    this.userRepository = userRepository;
    this.teaFavoriteRepository = teaFavoriteRepository;
    this.teaRepository = teaRepository;
  }

  @Transactional(readOnly = true)
  public UserFavoritesResponse findFavorites(String userId, int page, int pageSize) {
    UserResponse user = userRepository.findById(userId);
    if (user == null) {
      return null;
    }

    long offset = ((long) page - 1) * pageSize;
    long total = teaFavoriteRepository.countByUserId(user.getId());
    TeaPageResponse favorites = new TeaPageResponse(page, pageSize, total,
        teaFavoriteRepository.findByUserId(user.getId(), pageSize, offset));
    return new UserFavoritesResponse(user, favorites);
  }

  @Transactional
  public FavoriteCreationResult addFavorite(String userId, String teaId) {
    if (userRepository.findById(userId) == null || teaRepository.findById(teaId) == null) {
      return FavoriteCreationResult.RESOURCE_NOT_FOUND;
    }

    if (teaFavoriteRepository.add(userId, teaId)) {
      return FavoriteCreationResult.CREATED;
    }
    return FavoriteCreationResult.ALREADY_EXISTS;
  }

  @Transactional
  public boolean removeFavorite(String userId, String teaId) {
    if (userRepository.findById(userId) == null) {
      return false;
    }

    // 用户存在时，目标是确保收藏不存在，删除 0 条也代表目标已达到。
    teaFavoriteRepository.remove(userId, teaId);
    return true;
  }
}
