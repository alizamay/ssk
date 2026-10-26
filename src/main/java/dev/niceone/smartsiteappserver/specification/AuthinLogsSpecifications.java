package dev.niceone.smartsiteappserver.specification;


import dev.niceone.smartsiteappserver.model.AuthinLogs;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.LocalDateTime;

public class AuthinLogsSpecifications {

    public static Specification<AuthinLogs> siteCodeLike(String siteCode) {
        return (root, query, cb) -> siteCode == null || siteCode.isEmpty()
                ? null
                : cb.like(cb.lower(root.get("siteCode")), "%" + siteCode.toLowerCase() + "%");
    }

    public static Specification<AuthinLogs> isClear(Boolean isClear) {
        return (root, query, cb) -> isClear == null
                ? null
                : cb.equal(root.get("isClear"), isClear);
    }

    public static Specification<AuthinLogs> timestampBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            if (from != null && to != null)
                return cb.between(root.get("timestamp"), from, to);
            else if (from != null)
                return cb.greaterThanOrEqualTo(root.get("timestamp"), from);
            else if (to != null)
                return cb.lessThanOrEqualTo(root.get("timestamp"), to);
            else
                return null;
        };
    }

    public static Specification<AuthinLogs> filterBy(
            String siteCode,
            Boolean isClear,
            LocalDateTime start,
            LocalDateTime end) {
        return (Root<AuthinLogs> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Predicate predicate = cb.conjunction();

            if (siteCode != null && !siteCode.isEmpty()) {
                predicate = cb.and(predicate, cb.equal(root.get("siteCode"), siteCode));
            }

            if (isClear != null) {
                predicate = cb.and(predicate, cb.equal(root.get("isClear"), isClear));
            }

            if (start != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("timestamp"), start));
            }

            if (end != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("timestamp"), end));
            }

            return predicate;
        };
    }
}
