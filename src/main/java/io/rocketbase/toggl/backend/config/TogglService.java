package io.rocketbase.toggl.backend.config;

import ch.simas.jtoggl.JToggl;
import ch.simas.jtoggl.domain.User;
import ch.simas.jtoggl.domain.Workspace;
import com.timgroup.jgravatar.Gravatar;
import com.timgroup.jgravatar.GravatarDefaultImage;
import de.jollyday.HolidayCalendar;
import io.rocketbase.toggl.api.TogglReportApi;
import io.rocketbase.toggl.api.TogglReportApiBuilder;
import io.rocketbase.toggl.backend.model.ApplicationSettingModel;
import io.rocketbase.toggl.backend.model.ApplicationSettingModel.SchedulingConfig;
import io.rocketbase.toggl.backend.model.ApplicationSettingModel.UserDetails;
import io.rocketbase.toggl.backend.repository.ApplicationSettingRepository;
import io.rocketbase.toggl.backend.util.ColorPalette;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by marten on 09.03.17.
 */
@Component
public class TogglService implements TogglReportApiBuilder.WorkspaceProvider {

    public static final Gravatar GRAVATAR = new Gravatar(64, Gravatar.DEFAULT_RATING, GravatarDefaultImage.IDENTICON);

    @Resource
    private ApplicationSettingRepository applicationSettingRepository;

    private ApplicationSettingModel applicationSettings;

    @Getter
    private JToggl jToggl;

    @Getter
    private TogglReportApi togglReportApi;

    @PostConstruct
    public void init() {
        List<ApplicationSettingModel> entities = applicationSettingRepository.findAll();
        if (entities == null || entities.size() <= 0) {
            applicationSettings = applicationSettingRepository.save(ApplicationSettingModel.builder()
                    .currentWorkspaceId(-1)
                    .workspaceMap(new HashMap<>())
                    .userMap(new HashMap<>())
                    .build());
        } else {
            applicationSettings = entities.get(0);
        }
        initApis();
    }

    @Override
    public long getWorkspaceId() {
        return applicationSettings.getCurrentWorkspaceId();
    }

    public void setWorkspace(Workspace workspace) {
        if (workspace != null && applicationSettings.getCurrentWorkspaceId() != workspace.getId()) {
            applicationSettings.setCurrentWorkspaceId(workspace.getId());
            applicationSettings.getWorkspaceMap()
                    .putIfAbsent(workspace.getId(), workspace);

            List<User> workspaceUsers = jToggl.getWorkspaceUsers(workspace.getId());
            if (workspaceUsers != null) {
                workspaceUsers.forEach(u -> {
                    applicationSettings.getUserMap()
                            .computeIfAbsent(u.getId(), (id) -> {
                                UserDetails userDetails = new UserDetails(u.getId(), u.getFullname(), u.getEmail());
                                userDetails.setGraphColor(ColorPalette.getRandomValue());
                                userDetails.setAvatar(GRAVATAR.getUrl(u.getEmail()));
                                return userDetails;
                            });
                });
            }
            applicationSettingRepository.save(applicationSettings);
        }
    }


    public boolean isApiTokenAvailable() {
        return !StringUtils.isEmpty(applicationSettings.getApiToken());
    }

    public void updateToken(String apiToken) {
        applicationSettings.setApiToken(apiToken);
        applicationSettingRepository.save(applicationSettings);

        initApis();

        // in order to fire check
        try {
            jToggl.getCurrentUser();
        } catch (Exception e) {
            applicationSettings.setApiToken(null);
            applicationSettingRepository.save(applicationSettings);
            throw new RuntimeException("invalid api-token");
        }
    }

    private void initApis() {
        togglReportApi = new TogglReportApiBuilder()
                .apiToken(applicationSettings.getApiToken())
                .userAgent("toggl-reporter")
                .workspaceProvider(this)
                .build();

        jToggl = new JToggl(applicationSettings.getApiToken());
    }

    public Workspace getWorkspaceById(long workspaceId) {
        return applicationSettings.getWorkspaceMap()
                .getOrDefault(workspaceId, new Workspace());
    }

    public UserDetails getUserById(long userId) {
        return applicationSettings.getUserMap()
                .getOrDefault(userId, new UserDetails(userId, "", ""));
    }

    public List<UserDetails> getAllUsers() {
        return new ArrayList<>(applicationSettings.getUserMap()
                .values());
    }

    public void updateUser(UserDetails user) {
        applicationSettings.getUserMap()
                .put(user.getUid(), user);
        applicationSettingRepository.save(applicationSettings);
    }

    public HolidayCalendar getHolidayCalender() {
        return applicationSettings.getHolidayCalendar();
    }


    public void updateHolidayCalendar(HolidayCalendar holidayCalendar) {
        applicationSettings.setHolidayCalendar(holidayCalendar);
        applicationSettingRepository.save(applicationSettings);
    }

    public SchedulingConfig getSchedulingConfig() {
        return applicationSettings.getSchedulingConfig();
    }


    public void updateSchedulingConfig(SchedulingConfig schedulingConfig) {
        applicationSettings.setSchedulingConfig(schedulingConfig);
        applicationSettingRepository.save(applicationSettings);
    }
}
