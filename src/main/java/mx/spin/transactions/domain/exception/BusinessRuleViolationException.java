package mx.spin.transactions.domain.exception;

import mx.spin.transactions.domain.policy.RuleViolation;
import java.util.List;
import java.util.stream.Collectors;

public class BusinessRuleViolationException extends DomainException {

    private final List<RuleViolation> violations;

    public BusinessRuleViolationException(List<RuleViolation> violations) {
        super(violations.stream().map(RuleViolation::message).collect(Collectors.joining("; ")));
        this.violations = List.copyOf(violations);
    }

    public List<RuleViolation> violations() { return violations; }
    @Override public String code() { return "BUSINESS_RULE_VIOLATION"; }
}