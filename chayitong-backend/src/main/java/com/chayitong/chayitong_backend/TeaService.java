package com.chayitong.chayitong_backend;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeaService {

  private final TeaRepository teaRepository;

  public TeaService(TeaRepository teaRepository) {
    this.teaRepository = teaRepository;
  }

  @Transactional(readOnly = true)
  public TeaPageResponse listTeas(String type, int page, int pageSize) {
    // 先转成 long，避免大页码乘以 pageSize 时发生 int 溢出。
    long offset = ((long) page - 1) * pageSize;
    if (type == null || type.isBlank()) {
      long total = teaRepository.countAll();
      return new TeaPageResponse(page, pageSize, total,
          teaRepository.findAll(pageSize, offset));
    }

    long total = teaRepository.countByType(type);
    return new TeaPageResponse(page, pageSize, total,
        teaRepository.findByType(type, pageSize, offset));
  }

  // 只返回查询结果；HTTP 状态码由控制器决定。
  public TeaResponse findById(String id) {
    return teaRepository.findById(id);
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
