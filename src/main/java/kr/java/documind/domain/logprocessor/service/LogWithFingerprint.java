package kr.java.documind.domain.logprocessor.service;

import kr.java.documind.domain.issue.service.fingerprint.FingerprintResult;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;

/**
 * GameLog와 FingerprintResult를 함께 담는 wrapper
 *
 * <p>이슈 그룹핑 시 fingerprint quality 정보가 필요하므로 함께 전달
 */
public record LogWithFingerprint(GameLog log, FingerprintResult fingerprintResult) {}
