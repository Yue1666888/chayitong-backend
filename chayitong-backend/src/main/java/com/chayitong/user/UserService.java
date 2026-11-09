package com.chayitong.user;

import com.chayitong.auth.AuthenticatedUser;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserMapper userMapper;
    private final PointLedgerMapper pointLedgerMapper;
    private final FavoriteService favoriteService;
    private final HistoryService historyService;
    private final JdbcTemplate jdbcTemplate;

    public UserService(UserMapper userMapper, PointLedgerMapper pointLedgerMapper, FavoriteService favoriteService,
            HistoryService historyService, JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.pointLedgerMapper = pointLedgerMapper;
        this.favoriteService = favoriteService;
        this.historyService = historyService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public DailyPointsResult claimDailyPoints(AuthenticatedUser currentUser) {
        UserEntity user = userMapper.lockById(currentUser.id());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        LocalDate businessDate = LocalDate.now(BUSINESS_ZONE);
        boolean alreadyClaimed = !jdbcTemplate.queryForList("SELECT id FROM point_ledger WHERE user_id = ? "
                + "AND rule_code = 'DAILY_CHECKIN' AND business_date = ? AND deleted = 0",
                Long.class, user.getId(), businessDate).isEmpty();
        if (alreadyClaimed) {
            return result(businessDate, false, user.getPointsBalance());
        }
        long points = user.getPointsBalance() + 5;
        PointLedger ledger = new PointLedger();
        ledger.setUserId(user.getId());
        ledger.setRuleCode("DAILY_CHECKIN");
        ledger.setBusinessDate(businessDate);
        ledger.setDeltaPoints(5);
        ledger.setBalanceAfter(points);
        ledger.setDescription("每日登录积分");
        pointLedgerMapper.insert(ledger);
        user.setPointsBalance(points);
        userMapper.updateById(user);
        return result(businessDate, true, points);
    }

    public UserSummary summary(AuthenticatedUser currentUser) {
        UserEntity user = userMapper.selectById(currentUser.id());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long postCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post WHERE author_id = ? "
                + "AND status IN ('published','hidden') AND deleted = 0", Long.class, user.getId());
        long points = user.getPointsBalance();
        return new UserSummary(favoriteService.countActive(user.getId(), null), historyService.count(user.getId()),
                postCount == null ? 0 : postCount, points, LevelInfo.of(points));
    }

    private DailyPointsResult result(LocalDate date, boolean awarded, long points) {
        return new DailyPointsResult(date, awarded, points, LevelInfo.of(points));
    }
}
