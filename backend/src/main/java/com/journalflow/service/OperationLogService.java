package com.journalflow.service;

import com.journalflow.entity.OperationLog;
import com.journalflow.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationLogService {
    private final OperationLogMapper operationLogMapper;

    public void write(Long userId, String action, String objectType, Long objectId,
                      String detail, String ip, boolean success) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setDetail(detail);
        log.setIp(ip);
        log.setSuccess(success ? 1 : 0);
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }
}
