'use client';

import { useEffect, useState } from 'react';

type DemoState = 'ready' | 'scanning' | 'added';

export function ShikeDemo() {
  const [state, setState] = useState<DemoState>('ready');

  useEffect(() => {
    if (state !== 'scanning') return;
    const timer = window.setTimeout(() => setState('added'), 900);
    return () => window.clearTimeout(timer);
  }, [state]);

  return (
    <div className="shikeDeviceStage">
      <div className="phoneShadow" aria-hidden="true" />
      <div className="shikePhone">
        <div className="phoneStatus">
          <span>9:41</span><span>食刻</span><span aria-hidden="true">⚙</span>
        </div>
        <div className="dashboardDate">
          <div><span>今天</span><strong>8月22日 · 星期六</strong></div>
          <i>连续记录 184 天</i>
        </div>
        <div className="dailySummaryCard">
          <span className="summaryLabel">今日摄入</span>
          <strong className="calorieTotal">1,268 <small>/ 2,000 千卡</small></strong>
          <div className="calorieProgress" aria-label="今日热量目标完成 63%"><span /></div>
          <span className="remainingCalories">还可摄入 732 千卡</span>
          <div className="macroSummary">
            <span><i>蛋白质</i><b>68g</b></span>
            <span><i>碳水</i><b>142g</b></span>
            <span><i>脂肪</i><b>43g</b></span>
          </div>
        </div>
        <button
          className="analyzeButton"
          type="button"
          onClick={() => setState(state === 'added' ? 'ready' : 'scanning')}
          disabled={state === 'scanning'}
        >
          {state === 'ready'
            ? '◎　拍照记录这一餐'
            : state === 'scanning'
              ? '视觉模型分析中…'
              : '海南鸡饭已加入　✓'}
        </button>
        <div className="mealList" aria-live="polite">
          <div className="mealListHeader"><span>今天的记录</span><small>{state === 'added' ? '3 餐' : '2 餐'}</small></div>
          {state === 'added' ? (
            <div className="dashboardMealRow isNew">
              <span><b>海南鸡饭</b><small>刚刚 · 午餐</small></span><strong>620</strong>
            </div>
          ) : null}
          <div className="dashboardMealRow"><span><b>酸奶与香蕉</b><small>08:20 · 早餐</small></span><strong>248</strong></div>
          <div className="dashboardMealRow"><span><b>美式咖啡</b><small>10:35 · 加餐</small></span><strong>12</strong></div>
        </div>
      </div>
    </div>
  );
}
