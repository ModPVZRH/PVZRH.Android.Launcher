#nullable enable

using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Threading;
using BepInEx.Unity.IL2CPP;
using Il2CppInterop.Runtime.Injection;
using UnityEngine;

namespace PVZRH.LauncherUi;

/// <summary>
/// Detects the Unity main thread and runs work there.
/// Use this when reading game objects (plants, Board, etc.) that crash off-thread
/// or before resources are ready.
/// </summary>
public static class UnityDispatch
{
    private static int _mainThreadId;
    private static readonly ConcurrentQueue<Action> Posts = new();
    private static readonly List<WhenJob> Waiting = new();
    private static readonly object WaitingLock = new();
    private static bool _started;

    /// <summary>True when the current thread is Unity's main thread (after the dispatcher has started).</summary>
    public static bool IsMainThread =>
        _mainThreadId != 0 && Thread.CurrentThread.ManagedThreadId == _mainThreadId;

    /// <summary>True after <see cref="LauncherUiPlugin"/> has attached the Unity pump.</summary>
    public static bool IsReady => _mainThreadId != 0;

    /// <summary>
    /// Queues <paramref name="action"/> for the next Unity Update.
    /// If already on the main thread, runs immediately.
    /// </summary>
    public static void Post(Action action)
    {
        if (action is null) throw new ArgumentNullException(nameof(action));
        if (IsMainThread)
        {
            SafeRun(action);
            return;
        }

        Posts.Enqueue(action);
    }

    /// <summary>
    /// Each Unity frame, evaluates <paramref name="ready"/> on the main thread.
    /// When it returns true, runs <paramref name="action"/> once.
    /// Exceptions in <paramref name="ready"/> are treated as "not ready".
    /// </summary>
    /// <param name="ready">Return true when game data is safe to read.</param>
    /// <param name="action">Runs once on the Unity main thread after <paramref name="ready"/> succeeds.</param>
    /// <param name="timeoutMs">0 waits indefinitely. After timeout the job is dropped.</param>
    public static void RunWhen(Func<bool> ready, Action action, int timeoutMs = 0)
    {
        if (ready is null) throw new ArgumentNullException(nameof(ready));
        if (action is null) throw new ArgumentNullException(nameof(action));

        var job = new WhenJob(ready, action, timeoutMs <= 0 ? 0 : Environment.TickCount + timeoutMs);
        lock (WaitingLock)
        {
            if (Waiting.Count >= 64)
            {
                Waiting.RemoveAt(0);
            }

            Waiting.Add(job);
        }
    }

    internal static void Start(BasePlugin plugin)
    {
        if (_started) return;
        _started = true;
        ClassInjector.RegisterTypeInIl2Cpp<Pump>();
        plugin.AddComponent<Pump>();
    }

    internal static void Tick()
    {
        if (_mainThreadId == 0)
        {
            _mainThreadId = Thread.CurrentThread.ManagedThreadId;
        }

        while (Posts.TryDequeue(out var posted))
        {
            SafeRun(posted);
        }

        List<WhenJob>? due = null;
        lock (WaitingLock)
        {
            for (var i = Waiting.Count - 1; i >= 0; i--)
            {
                var job = Waiting[i];
                if (job.DeadlineTicks != 0 && Environment.TickCount - job.DeadlineTicks >= 0)
                {
                    Waiting.RemoveAt(i);
                    continue;
                }

                bool ok;
                try
                {
                    ok = job.Ready();
                }
                catch
                {
                    ok = false;
                }

                if (!ok) continue;
                Waiting.RemoveAt(i);
                due ??= new List<WhenJob>();
                due.Add(job);
            }
        }

        if (due == null) return;
        for (var i = due.Count - 1; i >= 0; i--)
        {
            SafeRun(due[i].Run);
        }
    }

    private static void SafeRun(Action action)
    {
        try
        {
            action();
        }
        catch (Exception ex)
        {
            LauncherUiPlugin.Instance?.Log.LogError($"UnityDispatch: {ex}");
        }
    }

    private sealed class WhenJob
    {
        public WhenJob(Func<bool> ready, Action run, int deadlineTicks)
        {
            Ready = ready;
            Run = run;
            DeadlineTicks = deadlineTicks;
        }

        public Func<bool> Ready { get; }
        public Action Run { get; }
        public int DeadlineTicks { get; }
    }

    internal sealed class Pump : MonoBehaviour
    {
        private void Update() => Tick();
    }
}
