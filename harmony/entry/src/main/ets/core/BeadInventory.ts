// 移植自 BeadInventory.java
// 存储介质差异:安卓版持久化到 files/inventory.json(JSON);
// 鸿蒙版按移植规范改用 @kit.ArkData 的 Preferences,每个色值一个键:
// 'inv_' + rgb 的 6 位 hex。对外语义与安卓一致:
// get 返回 -1 表示"从未登记过",0 表示"登记过但已用完";
// set 会把负数钳制为 0(同安卓 Math.max(0, count))。
import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';

const STORE_NAME: string = 'pindou_inventory';
const KEY_PREFIX: string = 'inv_';

/** 同安卓 String.format("%06x", rgb & 0xFFFFFF):小写 6 位十六进制 */
function keyOf(rgb: number): string {
  const v: number = rgb & 0xFFFFFF;
  let hex: string = v.toString(16);
  while (hex.length < 6) {
    hex = '0' + hex;
  }
  return KEY_PREFIX + hex;
}

/** 手头数量;-1 = 未登记 */
export async function invGet(ctx: common.UIAbilityContext, rgb: number): Promise<number> {
  const store: preferences.Preferences = await preferences.getPreferences(ctx, STORE_NAME);
  const v: preferences.ValueType = await store.get(keyOf(rgb), -1);
  return typeof v === 'number' ? v : -1;
}

/** 登记/更新手头数量(负数按 0 处理,同安卓) */
export async function invSet(ctx: common.UIAbilityContext, rgb: number, count: number): Promise<void> {
  const store: preferences.Preferences = await preferences.getPreferences(ctx, STORE_NAME);
  await store.put(keyOf(rgb), Math.max(0, count));
  await store.flush();
}
