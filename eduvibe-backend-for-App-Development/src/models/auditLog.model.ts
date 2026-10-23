import mongoose, { Schema, Document, Types } from 'mongoose';

export interface IAuditLog extends Document {
  adminId: Types.ObjectId;
  action: string;
  targetId: Types.ObjectId;
  targetType: 'teacher' | 'user' | 'class';
  metadata: Record<string, unknown>;
  createdAt: Date;
}

const auditLogSchema = new Schema<IAuditLog>(
  {
    adminId: { type: Schema.Types.ObjectId, ref: 'User', required: true },
    action: { type: String, required: true },
    targetId: { type: Schema.Types.ObjectId, required: true },
    targetType: { type: String, enum: ['teacher', 'user', 'class'], required: true },
    metadata: { type: Schema.Types.Mixed, default: {} },
  },
  { timestamps: true }
);

auditLogSchema.index({ adminId: 1 });
auditLogSchema.index({ action: 1 });
auditLogSchema.index({ targetId: 1 });
auditLogSchema.index({ createdAt: -1 });

export const AuditLog = mongoose.model<IAuditLog>('AuditLog', auditLogSchema);
