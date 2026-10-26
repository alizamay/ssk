package dev.niceone.smartsiteappserver.specification;

import dev.niceone.smartsiteappserver.model.SiteEventLog;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SiteEventLogSpecification {

    public static Specification<SiteEventLog> unresolvedWithKeywordAndDate(
            String keyword, LocalDateTime start, LocalDateTime end
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // เงื่อนไข is_resolve = false
            predicates.add(cb.isFalse(root.get("isResolve")));

            // เงื่อนไขช่วงเวลา
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createDate"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createDate"), end));
            }

            // keyword ในหลาย field
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";

                Predicate p1 = cb.like(cb.lower(root.get("alarmTypeName")), likeKeyword);
                Predicate p2 = cb.like(cb.lower(root.get("siteCode")), likeKeyword);
                Predicate p3 = cb.like(cb.lower(root.get("regionCode")), likeKeyword);
                Predicate p4 = cb.like(cb.lower(root.get("cabinetCode")), likeKeyword);

                predicates.add(cb.or(p1, p2, p3, p4));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}