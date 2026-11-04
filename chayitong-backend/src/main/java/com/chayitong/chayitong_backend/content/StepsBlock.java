package com.chayitong.chayitong_backend.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class StepsBlock implements ContentBlock {
  @NotEmpty(message = "步骤不能为空")
  private List<@NotNull(message = "步骤不能为 null") @Valid StepItem> items;

  @Override
  public String getType() {
    return "steps";
  }

  public List<StepItem> getItems() {
    return items;
  }

  public void setItems(List<StepItem> items) {
    this.items = items;
  }
}
