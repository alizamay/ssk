package dev.niceone.smartsiteappserver.specification;

import dev.niceone.smartsiteappserver.model.Site;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Slf4j
public class SiteSpecification implements Specification<Site> {
    private SpecSearchCriteria criteria;

    public SiteSpecification(final SpecSearchCriteria criteria) {
        super();
        this.criteria = criteria;
    }

    public SpecSearchCriteria getCriteria() {
        return criteria;
    }

    @Override
    public Predicate toPredicate(final Root<Site> root, final CriteriaQuery<?> query, final CriteriaBuilder builder) {
        switch (criteria.getOperation()) {
            case EQUALITY:
                return builder.equal(root.get(criteria.getKey()), criteria.getValue());
            case NEGATION:
                return builder.notEqual(root.get(criteria.getKey()), criteria.getValue());
            case GREATER_THAN:
                return builder.greaterThan(root.get(criteria.getKey()), criteria.getValue().toString());
            case LESS_THAN:
                return builder.lessThan(root.get(criteria.getKey()), criteria.getValue().toString());
            case LIKE:
                return builder.like(root.get(criteria.getKey()), criteria.getValue().toString());
            case STARTS_WITH:
                return builder.like(root.get(criteria.getKey()), criteria.getValue() + "%");
            case ENDS_WITH:
                return builder.like(root.get(criteria.getKey()), "%" + criteria.getValue());
            case CONTAINS:
                return builder.like(root.get(criteria.getKey()), "%" + criteria.getValue() + "%");
            case IN:
                return builder.in(root.get(criteria.getKey())).value(criteria.getValue());
            default:
                return null;
        }
    }

    public static Specification<Site> longTimeUpdateKeyGenerate(int seconds) {
        return new Specification<Site>() {
            public Predicate toPredicate(Root<Site> root, CriteriaQuery<?> query,
                                         CriteriaBuilder builder) {

//                LocalDateTime dt = LocalDateTime.now().minusMinutes(60);
                LocalDateTime dt = LocalDateTime.now().minusSeconds(seconds-1);
                return builder.lessThan(root.<LocalDateTime>get("lastKeyGenerate"), dt);
            }
        };
    }

    public static Specification<Site> neverUpdateKeyGenerate() {
        return new Specification<Site>() {
            public Predicate toPredicate(Root<Site> root, CriteriaQuery<?> query,
                                         CriteriaBuilder builder) {
                return builder.isNull(root.<LocalDateTime>get("lastKeyGenerate"));
            }
        };
    }
}
