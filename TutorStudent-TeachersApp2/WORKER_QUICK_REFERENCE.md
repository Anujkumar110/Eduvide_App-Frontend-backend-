# Worker Quick Reference Guide

## Starting Backend + Workers

### Option 1: Production Mode
```bash
# Terminal 1 - API Server
npm start

# Terminal 2 - Workers
npm run worker
```

### Option 2: Development Mode (Auto-reload)
```bash
# Terminal 1 - API Server (auto-reload on file changes)
npm run dev

# Terminal 2 - Workers (auto-reload on file changes)
npm run dev:worker
```

---

## What Workers Do

### Session Worker (Runs automatically)
| Task | Frequency | What It Does |
|------|-----------|--------------|
| Auto-start sessions | Every 30 sec | Finds upcoming sessions starting soon → marks as `ongoing` |
| Auto-end sessions | Every 30 sec | Finds ongoing sessions past end time → marks as `completed` |
| Mark missed | Every 2 min | Finds upcoming sessions >15 min overdue → marks as `missed` |

### Slot Worker (Runs automatically)
| Task | Frequency | What It Does |
|------|-----------|--------------|
| Update slot statuses | Every 1 min | Marks booked slots past end time → `completed` |
| Auto-generate slots | Every 5 min | Creates slots for LIVE teachers without today's slots |

---

## Expected Log Output

### When Worker Starts
```
[SessionWorker] 🚀 Starting session automation worker...
[SessionWorker] ⏰ Schedule:
[SessionWorker]   - Auto-start: Every 30 seconds
[SessionWorker]   - Auto-end: Every 30 seconds
[SessionWorker]   - Missed detection: Every 2 minutes
[SessionWorker] ✅ Worker scheduled successfully

[SlotWorker] 🚀 Starting slot automation worker...
[SlotWorker] ⏰ Schedule:
[SlotWorker]   - Update slot statuses: Every 1 minute
[SlotWorker]   - Auto-generate slots: Every 5 minutes
[SlotWorker] ✅ Worker scheduled successfully
```

### When Session Auto-Starts
```
[SessionWorker] ⏰ Auto-starting session: 67abc... (Math Class) at 2026-04-15T10:00:30.000Z
[SessionWorker] ✅ Auto-started 1 sessions at 2026-04-15T10:00:30.000Z
```

### When Slot Auto-Completes
```
[SlotWorker] ✅ Slot 123def... (09:00-10:00) marked as completed
[SlotWorker] ✅ Updated 1 slot statuses at 2026-04-15T10:01:00.000Z
```

### When Slots Auto-Generate
```
[SlotWorker] ✅ Auto-generated 8 slots for teacher John Doe
```

---

## Troubleshooting

### Workers Not Starting?
1. Check MongoDB connection:
   ```
   Worker connected to MongoDB successfully
   ```

2. Check Redis connection (if using BullMQ):
   ```
   Redis connected successfully
   ```

3. Verify `.env` has:
   ```env
   MONGO_URI=mongodb+srv://...
   REDIS_HOST=...
   REDIS_PASSWORD=...
   REDIS_PORT=19371
   ```

### Sessions Not Auto-Starting?
1. Check session `startTime` is in the past (or within 30 seconds)
2. Check session status is `upcoming` (not `ongoing` or `completed`)
3. Verify server time is correct (workers use UTC server time)
4. Check worker logs for errors

### Slots Not Auto-Generating?
1. Teacher must have `isLive: true` in profile
2. Teacher must have `availability` set for current day
3. No slots should already exist for today
4. Check worker logs: `[SlotWorker]` messages

### WebSocket Notifications Not Working?
1. Verify `WS_URL` in `.env` is correct
2. Check WebSocket connection in Android app logs
3. Verify `connectionManager.send()` is called in worker
4. Check student/teacher are connected to WebSocket

---

## Testing Workers Manually

### Test Auto-Start Session
```javascript
// In MongoDB or backend script
db.sessions.insertOne({
  classId: ObjectId("..."),
  teacherId: ObjectId("..."),
  startTime: new Date(Date.now() + 10000), // 10 seconds from now
  endTime: new Date(Date.now() + 60000),   // 1 minute from now
  status: 'upcoming'
})
// Wait 30 seconds - should auto-start
```

### Test Auto-End Session
```javascript
// Create ongoing session that already ended
db.sessions.updateOne(
  { _id: ObjectId("...") },
  { 
    $set: { 
      status: 'ongoing',
      endTime: new Date(Date.now() - 5000) // 5 seconds ago
    }
  }
)
// Wait 30 seconds - should auto-end
```

### Test Slot Auto-Generate
```javascript
// Make teacher LIVE
db.teacherprofiles.updateOne(
  { userId: ObjectId("...") },
  { $set: { isLive: true } }
)

// Delete today's slots
db.slots.deleteMany({
  teacherId: ObjectId("..."),
  date: new Date()
})

// Wait 5 minutes - should auto-generate
```

---

## Stopping Workers

### Graceful Shutdown
```bash
# Press Ctrl+C in worker terminal
# Workers will:
# 1. Log "SIGINT received"
# 2. Close MongoDB connection
# 3. Exit cleanly
```

### Force Stop
```bash
# Find worker process
ps aux | grep ts-node

# Kill it
kill -9 <PID>
```

---

## Monitoring Workers

### Check Worker Status
```bash
# See worker logs (tail)
tail -f logs/worker.log

# Or watch live logs
npm run dev:worker
```

### Check Session Status in DB
```javascript
// See upcoming sessions
db.sessions.find({ status: 'upcoming' }).sort({ startTime: 1 })

// See ongoing sessions
db.sessions.find({ status: 'ongoing' })

// See recently completed
db.sessions.find({ status: 'completed' }).sort({ actualEndTime: -1 }).limit(5)
```

### Check Slot Status in DB
```javascript
// See today's slots
db.slots.find({ 
  date: new Date(),
  teacherId: ObjectId("...")
}).sort({ startTime: 1 })

// See available slots
db.slots.find({ status: 'available' })

// See booked slots
db.slots.find({ status: 'booked' })
```

---

## Common Issues

### Issue: Sessions start/end at wrong time
**Solution**: Check server timezone. Workers use `new Date()` which is UTC. Make sure session times are stored in UTC.

### Issue: Multiple workers running
**Solution**: Only run ONE worker process. Multiple workers will duplicate tasks.

### Issue: Worker crashes on startup
**Solution**: 
1. Check MongoDB is running
2. Check Redis is running
3. Verify `.env` variables
4. Check `npm install` completed

### Issue: WebSocket notifications not received
**Solution**:
1. Verify client is connected to WebSocket server
2. Check `connectionManager.send()` has correct userId
3. Verify userId is a string, not ObjectId
4. Check Android WebSocket manager is initialized

---

## Worker Architecture

```
┌──────────────────────────────────────┐
│         worker.ts (Entry Point)      │
│                                      │
│  1. Connect to MongoDB              │
│  2. Import workers                  │
│  3. Start session worker            │
│  4. Start slot worker               │
│  5. Handle graceful shutdown        │
└──────────────────────────────────────┘
           │
           ├──► session.worker.ts
           │     - autoStartSessions()
           │     - autoEndSessions()
           │     - markMissedSessions()
           │
           └──► slot.worker.ts
                 - updateSlotStatuses()
                 - autoGenerateSlotsForLiveTeachers()
```

---

## Performance Tips

1. **Run workers on separate server** in production
2. **Use PM2** for process management:
   ```bash
   pm2 start dist/worker.js --name eduvibe-worker
   pm2 logs eduvibe-worker
   ```
3. **Monitor MongoDB queries** - Add indexes if workers are slow
4. **Use Redis queues** for heavy tasks (already set up with BullMQ)
5. **Log rotation** - Prevent log files from growing too large

---

**Remember**: Workers run continuously. Always test in development before deploying to production!
