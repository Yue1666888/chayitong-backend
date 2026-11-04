package com.chayitong.chayitong_backend;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeaService {

  private final TeaRepository teaRepository;
  private final TeaMapper teaMapper;

  public TeaService(TeaRepository teaRepository, TeaMapper teaMapper) {
    this.teaRepository = teaRepository;
    this.teaMapper = teaMapper;
  }

  @Transactional(readOnly = true)
  public TeaPageResponse listTeas(String type, String keyword, int page, int pageSize) {
    // 先转成 long，避免大页码乘以 pageSize 时发生 int 溢出。
    long offset = ((long) page - 1) * pageSize;
    String typeFilter = type == null || type.isBlank() ? null : type;
    String namePattern = keyword == null || keyword.isBlank()
        ? null : SqlLikePattern.contains(keyword.strip());
    long total = teaMapper.countMatching(typeFilter, namePattern);
    return new TeaPageResponse(page, pageSize, total,
        teaMapper.findPage(typeFilter, namePattern, pageSize, offset));
  }

  // 只返回查询结果；HTTP 状态码由控制器决定。
  public TeaResponse findById(String id) {
    TeaEntity tea = teaMapper.selectById(id);
    if (tea == null) {
      return null;
    }
    return new TeaResponse(tea.getId(), tea.getName(), tea.getType());
  }

  public TeaResponse createTea(String id, String name, String type) {
    TeaResponse tea = new TeaResponse(id, name, type);
    teaRepository.insert(tea);
    return tea;
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

  public TeaResponse updateTea(String id, String name, String type) {
    teaRepository.update(id, name, type);
    // 回查实际记录：编号不存在时返回 null；值未变化时仍然返回现有记录。
    return teaRepository.findById(id);
  }

  public boolean deleteTea(String id) {
    return teaRepository.deleteById(id) > 0;
  }
}
