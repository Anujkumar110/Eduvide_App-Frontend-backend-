import cron from 'node-cron';
import mongoose from 'mongoose';
import { TeacherProfile } from '../models/teacherProfile.model';
import { ITeacherAvailability } from '../models/availability.model';
import { Slot } from '../models/slot.model';
import { Class } from '../models/class.model';

// Maps JS getDay() index → availability key
const DAY_MAP: Record<number, keyof ITeacherAvailability> = {
    0: 'sunday',
    1: 'monday',
    2: 'tuesday',
    3: 'wednesday',
    4: 'thursday',
    5: 'friday',
    6: 'saturday',
};

function getTargetDate(daysAhead = 1): Date {
    const date = new Date();
    date.setUTCDate(date.getUTCDate() + daysAhead);
    date.setUTCHours(0, 0, 0, 0);
    return date;
}

async function generateSlotsForTeacher(
    teacherId: mongoose.Types.ObjectId,
    availability: ITeacherAvailability,
    targetDate: Date
): Promise<{ created: number; skipped: number }> {
    const dayKey = DAY_MAP[targetDate.getUTCDay()];
    const dayAvailability = availability[dayKey];

    if (!dayAvailability?.isAvailable || !dayAvailability.slots?.length) {
        return { created: 0, skipped: 0 };
    }

    let created = 0;
    let skipped = 0;

    for (const { startTime, endTime } of dayAvailability.slots) {
        const exists = await Slot.exists({ teacherId, date: targetDate, startTime, endTime });

        if (exists) {
            skipped++;
            continue;
        }

        await Slot.create({
            teacherId,
            date: targetDate,
            startTime,
            endTime,
            status: 'available',
        });

        created++;
    }

    return { created, skipped };
}

async function runSlotGeneration(): Promise<void> {
    console.log(`[SlotJob] Starting slot generation — ${new Date().toISOString()}`);

    const targetDate = getTargetDate(1);
    console.log(`[SlotJob] Target date: ${targetDate.toISOString().split('T')[0]}`);

    const teachers = await TeacherProfile.find(
        { isActive: true },
        { _id: 1, availability: 1 }
    ).lean();

    if (!teachers.length) {
        console.log('[SlotJob] No active teachers found. Exiting.');
        return;
    }

    console.log(`[SlotJob] Processing ${teachers.length} active teacher(s)…`);

    const results = await Promise.allSettled(
        teachers.map((teacher) =>
            generateSlotsForTeacher(teacher._id, teacher.availability, targetDate)
        )
    );

    let totalCreated = 0;
    let totalSkipped = 0;
    let totalFailed = 0;

    results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
            totalCreated += result.value.created;
            totalSkipped += result.value.skipped;
        } else {
            totalFailed++;
            console.error(
                `[SlotJob] Failed for teacherId=${teachers[index]._id}:`,
                result.reason
            );
        }
    });

    console.log(
        `[SlotJob] Done — created: ${totalCreated}, skipped: ${totalSkipped}, failed: ${totalFailed}`
    );
}

// ─── AUTO END ONGOING CLASSES ─────────────────────────────────────────────────
// Har minute check karo — agar koi ongoing class hai jiska endTime nikal gaya
// toh usse completed mark karo
async function autoEndExpiredClasses(): Promise<void> {
    const now = new Date();

    try {
        // Wo classes jo ongoing hain aur jinki schedule[0].endTime nikal gayi
        const expiredClasses = await Class.find({
            status: 'ongoing',
            'schedule.0.endTime': { $lte: now },
        });

        if (!expiredClasses.length) return;

        console.log(`[AutoEndJob] Found ${expiredClasses.length} expired class(es) — marking completed`);

        for (const cls of expiredClasses) {
            cls.status = 'completed';
            await cls.save();
            console.log(`[AutoEndJob] Class ${cls._id} (${cls.title}) marked completed`);
        }
    } catch (error) {
        console.error('[AutoEndJob] Error:', error);
    }
}

export function scheduleSlotGenerationJob(): void {
    const CRON_EXPRESSION = '0 0 * * *'; // daily at midnight

    cron.schedule(CRON_EXPRESSION, async () => {
        try {
            await runSlotGeneration();
        } catch (error) {
            console.error('[SlotJob] Unexpected error during slot generation:', error);
        }
    }, {
        timezone: 'UTC',
    });

    console.log(`[SlotJob] Scheduled — expression: "${CRON_EXPRESSION}" (UTC)`);
}

// ─── AUTO END JOB — har minute chalega ──────────────────────────────────────
export function scheduleAutoEndJob(): void {
    cron.schedule('* * * * *', async () => {
        try {
            await autoEndExpiredClasses();
        } catch (error) {
            console.error('[AutoEndJob] Unexpected error:', error);
        }
    });

    console.log('[AutoEndJob] Scheduled — runs every minute');
}

export { runSlotGeneration };