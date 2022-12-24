package info.neu.infoapp.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.io.Serializable;

@Data
public class LinkedPlanService implements Serializable {
    LinkedService linkedService;
    PlanCostShares planserviceCostShares;
    @Id
    private String objectId;
    private String _org;
    private String objectType;
}
