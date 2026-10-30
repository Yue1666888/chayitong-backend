package com.chayitong.chayitong_backend;

import org.springframework.security.crypto.password.PasswordEncoder;

// 独立运行 main 即可观察密码编码和匹配；不连接数据库或创建登录账户。
public class PasswordDemo {

  public static void main(String[] args) {
    PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    // 仅用于演示的公开字符串，不是 MySQL 密码或真实用户密码。
    String demoPassword = "TeaDemo-2026!";
    String firstHash = encoder.encode(demoPassword);
    String secondHash = encoder.encode(demoPassword);

    System.out.println("第一次编码：" + firstHash);
    System.out.println("第二次编码：" + secondHash);
    System.out.println("两个编码字符串相同：" + firstHash.equals(secondHash));
    System.out.println("正确密码匹配：" + encoder.matches(demoPassword, firstHash));
    System.out.println("错误密码匹配：" + encoder.matches("Wrong-Demo-Password", firstHash));
  }
}
