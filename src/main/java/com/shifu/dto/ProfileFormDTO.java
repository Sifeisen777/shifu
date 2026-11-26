package com.shifu.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 个人资料编辑表单。
 * 昵称/头像存放在 sys_user，其余字段存放在 sys_user_profile，
 * 这里合并成一个表单对象，避免前端分两次提交。
 *
 * @author 李勇
 */
@Data
public class ProfileFormDTO {

    /** 昵称 */
    private String nickName;

    /** 头像地址 */
    private String icon;

    /** 个人简介 */
    private String introduce;

    /** 所在城市 */
    private String city;

    /** 性别：true 男，false 女，null 未设置 */
    private Boolean gender;

    /** 生日 */
    private LocalDate birthday;
}
