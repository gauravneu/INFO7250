package info.neu.infoapp.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.io.Serializable;

@Data
public class PlanCostShares implements Serializable {
    @Id
    private String objectId;
    private int deductible;
    private int copay;
    private String _org;
    private String objectType;
}
