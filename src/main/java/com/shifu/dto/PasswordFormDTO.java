package com.shifu.dto;

import lombok.Data;

/**
 * 设置或修改登录密码的表单。
 * 首次设置时 oldPassword 留空；已设置过密码时必填。
 */
@Data
public class PasswordFormDTO {
    private String password;
    private String oldPassword;
}
