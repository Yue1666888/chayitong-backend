package com.chayitong.chayitong_backend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeaService {

  private final TeaMapper teaMapper;

  public TeaService(TeaMapper teaMapper) {
    this.teaMapper = teaMapper;
  }

  @Transactional(readOnly = true)
  public TeaPageResponse listTeas(String type, String keyword, int page, int pageSize) {
    LambdaQueryWrapper<TeaEntity> query = new LambdaQueryWrapper<>();
    query.eq(type != null && !type.isBlank(), TeaEntity::getType, type);
    if (keyword != null && !keyword.isBlank()) {
      query.apply("name LIKE {0} ESCAPE '!'", SqlLikePattern.contains(keyword.strip()));
    }
    query.orderByAsc(TeaEntity::getId);

    Page<TeaEntity> result = teaMapper.selectPage(new Page<>(page, pageSize), query);
    List<TeaResponse> items = result.getRecords().stream().map(this::toTeaResponse).toList();
    return new TeaPageResponse(page, pageSize, result.getTotal(), items);
  }

  // 只返回查询结果；HTTP 状态码由控制器决定。
  public TeaResponse findById(String id) {
    TeaEntity tea = teaMapper.selectById(id);
    if (tea == null) {
      return null;
    }
    return toTeaResponse(tea);
  }

  public TeaResponse createTea(String id, String name, String type) {
    teaMapper.insert(newTeaEntity(id, name, type));
    return new TeaResponse(id, name, type);
  }

  // 由控制器调用这个方法，Spring 为整批写入开启同一个事务。
  @Transactional
  public List<TeaResponse> createTeas(List<TeaCreateRequest> requests) {
    List<TeaResponse> teas = new ArrayList<>();
    for (TeaCreateRequest request : requests) {
      teas.add(createTea(request.getId(), request.getName(), request.getType()));
    }
    return teas;
  }

  @Transactional
  public TeaResponse updateTea(String id, String name, String type) {
    teaMapper.updateById(newTeaEntity(id, name, type));
    // 回查实际记录：编号不存在时返回 null；值未变化时仍然返回现有记录。
    return findById(id);
  }

  public boolean deleteTea(String id) {
    return teaMapper.deleteById(id) > 0;
  }

  private TeaResponse toTeaResponse(TeaEntity tea) {
    return new TeaResponse(tea.getId(), tea.getName(), tea.getType());
  }

  private TeaEntity newTeaEntity(String id, String name, String type) {
    TeaEntity tea = new TeaEntity();
    tea.setId(id);
    tea.setName(name);
    tea.setType(type);
    return tea;
  }
}
