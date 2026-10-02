// ICommandService.aidl
// 以 shell 权限执行命令的 UserService 接口。
package io.github.geekhonize.shakeoff;

interface ICommandService {
    /**
     * 执行一条命令并返回合并后的输出。
     *
     * @param cmd 命令与参数，例如 ["appops", "get", pkg, "OP_MOTION_SENSORS"]
     * @return 命令输出；失败时返回以 "ERROR:" 开头的字符串
     */
    String exec(in String[] cmd);
}
