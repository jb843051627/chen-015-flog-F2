package com.fc.v2.common.exception.flog;

/**
 * 建档卡立不住时抛出：回执必须点明缺的是哪一格 / 越过额度的是哪张卡，
 * 不许丢一句含糊的"数不像话"。由全局异常处理器原样转成页面回执文案。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class FlogCardRejectException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FlogCardRejectException(String message) {
        super(message);
    }
}
