package lt.reviv.bloodtests.controller.trend;

import lt.reviv.bloodtests.dto.trend.IndicatorHistoryResponse;
import lt.reviv.bloodtests.dto.trend.IndicatorOptionResponse;
import lt.reviv.bloodtests.service.trend.IndicatorTrendService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class IndicatorTrendController {

    private final IndicatorTrendService indicatorTrendService;

    public IndicatorTrendController(IndicatorTrendService indicatorTrendService) {
        this.indicatorTrendService = indicatorTrendService;
    }

    @GetMapping("/rodikliai")
    public List<IndicatorOptionResponse> listIndicators(@AuthenticationPrincipal Jwt jwt) {
        return indicatorTrendService.listIndicators(jwt);
    }

    @GetMapping("/rodikliai/{id}/istorija")
    public IndicatorHistoryResponse getIndicatorHistory(
            @PathVariable("id") Long indicatorId,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return indicatorTrendService.getHistory(jwt, indicatorId, period, from, to);
    }
}
