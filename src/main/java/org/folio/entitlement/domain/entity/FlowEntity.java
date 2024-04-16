package org.folio.entitlement.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "flow")
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FlowEntity extends AbstractFlowEntity {}
