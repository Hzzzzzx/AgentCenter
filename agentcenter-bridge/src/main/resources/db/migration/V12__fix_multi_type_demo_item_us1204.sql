-- V12: Correct the US1204 seed row from V11 where type/title were swapped.
UPDATE work_item
SET type = 'US',
    title = '消息中心订阅设置',
    updated_at = strftime('%Y-%m-%d %H:%M:%S', 'now')
WHERE id = '01WORKITEM0000000000000US1204'
  AND code = 'US1204'
  AND type = '消息中心订阅设置';
