/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.housekeeper.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.housekeeper.AlarmsDeletionHousekeeperTask;
import org.thingsboard.server.common.data.housekeeper.HousekeeperTaskType;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UUIDBased;
import org.thingsboard.server.dao.alarm.AlarmService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlarmsDeletionTaskProcessor extends HousekeeperTaskProcessor<AlarmsDeletionHousekeeperTask> {

    private final AlarmService alarmService;

    @Override
    public void process(AlarmsDeletionHousekeeperTask task) throws Exception {
        EntityId entityId = task.getEntityId();
        EntityType entityType = entityId.getEntityType();
        TenantId tenantId = task.getTenantId();

        if (entityType == EntityType.DEVICE || entityType == EntityType.ASSET) {
            if (task.getAlarms() == null) {
                AlarmId last = null;
                while (true) {
                    List<AlarmId> alarms = alarmService.findAlarmIdsByOriginatorIdAndIdOffset(tenantId, entityId, last, 128);
                    if (alarms.isEmpty()) {
                        break;
                    }

                    housekeeperClient.submitTask(new AlarmsDeletionHousekeeperTask(tenantId, entityId, alarms.stream()
                            .map(UUIDBased::getId).collect(Collectors.toList())));
                    last = alarms.get(alarms.size() - 1);
                    log.debug("[{}][{}][{}] Submitted task for deleting {} alarms", tenantId, entityType, entityId, alarms.size());
                }
            } else {
                for (UUID alarmId : task.getAlarms()) {
                    alarmService.delAlarm(tenantId, new AlarmId(alarmId));
                }
                log.debug("[{}][{}][{}] Deleted {} alarms", tenantId, entityType, entityId, task.getAlarms().size());
            }
        }

        int count = alarmService.deleteEntityAlarmRecords(tenantId, entityId);
        log.debug("[{}][{}][{}] Deleted {} entity alarms", tenantId, entityType, entityId, count);
    }

    @Override
    public HousekeeperTaskType getTaskType() {
        return HousekeeperTaskType.DELETE_ALARMS;
    }

}
