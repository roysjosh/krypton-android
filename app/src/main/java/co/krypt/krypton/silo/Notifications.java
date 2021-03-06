package co.krypt.krypton.silo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.widget.RemoteViews;

import javax.annotation.Nullable;

import co.krypt.kryptonite.MainActivity;
import co.krypt.krypton.R;
import co.krypt.krypton.log.Log;
import co.krypt.krypton.onboarding.OnboardingActivity;
import co.krypt.krypton.onboarding.OnboardingProgress;
import co.krypt.krypton.pairing.Pairing;
import co.krypt.krypton.pgp.PGPPublicKey;
import co.krypt.krypton.pgp.publickey.SignedPublicKeySelfCertification;
import co.krypt.krypton.policy.NoAuthReceiver;
import co.krypt.krypton.policy.Policy;
import co.krypt.krypton.policy.UnlockScreenDummyActivity;
import co.krypt.krypton.protocol.GitSignRequest;
import co.krypt.krypton.protocol.HostsRequest;
import co.krypt.krypton.protocol.MeRequest;
import co.krypt.krypton.protocol.Request;
import co.krypt.krypton.protocol.RequestBody;
import co.krypt.krypton.protocol.SignRequest;
import co.krypt.krypton.protocol.UnpairRequest;
import co.krypt.krypton.settings.Settings;

/**
 * Created by Kevin King on 12/5/16.
 * Copyright 2016. KryptCo, Inc.
 */

public class Notifications {
    private static final String ACTION_REQUIRED_CHANNEL_ID = "action-required";
    private static final String APPROVED_CHANNEL_ID = "approved";
    public static void setupNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel actionRequiredChannel = new NotificationChannel(ACTION_REQUIRED_CHANNEL_ID,
                    "Action Required",
                    NotificationManager.IMPORTANCE_HIGH);
            actionRequiredChannel.setDescription("Requests that require your explicit approval.");
            notificationManager.createNotificationChannel(actionRequiredChannel);

            NotificationChannel approvedChannel = new NotificationChannel(APPROVED_CHANNEL_ID,
                    "Approved",
                    NotificationManager.IMPORTANCE_HIGH);
            approvedChannel.setDescription("Automatically approved requests.");
            notificationManager.createNotificationChannel(approvedChannel);
        }
    }
    private static NotificationCompat.Builder buildNotification(Context context, String channelId) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_notification_white);
        return mBuilder;
    }
    public static void notifySuccess(Context context, Pairing pairing, Request request, @Nullable Log log) {
        if (!new Settings(context).approvedNotificationsEnabled()) {
            return;
        }
        Intent resultIntent = new Intent(context, MainActivity.class);
        Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder mBuilder =
                buildNotification(context, APPROVED_CHANNEL_ID)
                        .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (!new Settings(context).silenceNotifications()) {
            mBuilder.setSound(notificationSound)
                    .setVibrate(new long[]{0, 100});
        }
        request.body.visit(new RequestBody.Visitor<Void, RuntimeException>() {
            @Override
            public Void visit(MeRequest meRequest) throws RuntimeException {
                return null;
            }

            @Override
            public Void visit(SignRequest signRequest) throws RuntimeException {
                mBuilder
                        .setContentTitle("SSH Login Approved")
                        .setContentText(pairing.workstationName + ": " + signRequest.display());
                RemoteViews remoteViewsSmall = new RemoteViews(context.getPackageName(), R.layout.result_remote);
                remoteViewsSmall.setTextViewText(R.id.workstationName, pairing.workstationName);
                request.fillShortRemoteViews(remoteViewsSmall, true, log != null ? log.getSignature() : null);
                mBuilder.setContent(remoteViewsSmall);
                return null;
            }

            @Override
            public Void visit(GitSignRequest gitSignRequest) throws RuntimeException {
                mBuilder
                        .setContentTitle(gitSignRequest.title() + " Approved")
                        .setContentText(pairing.workstationName + ": " + gitSignRequest.display());

                RemoteViews remoteViewsSmall = new RemoteViews(context.getPackageName(), R.layout.result_remote);
                remoteViewsSmall.setTextViewText(R.id.workstationName, pairing.workstationName);
                gitSignRequest.fillShortRemoteViews(remoteViewsSmall, true, log != null ? log.getSignature() : null);
                mBuilder.setContent(remoteViewsSmall);

                RemoteViews remoteViewsBig = new RemoteViews(context.getPackageName(), R.layout.result_remote);
                remoteViewsBig.setTextViewText(R.id.workstationName, pairing.workstationName);
                gitSignRequest.fillRemoteViews(remoteViewsBig, true, log != null ? log.getSignature() : null);
                mBuilder.setCustomBigContentView(remoteViewsBig);
                return null;
            }

            @Override
            public Void visit(UnpairRequest unpairRequest) throws RuntimeException {
                return null;
            }

            @Override
            public Void visit(HostsRequest hostsRequest) throws RuntimeException {
                return null;
            }
        });

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);

        NotificationManagerCompat mNotifyMgr = NotificationManagerCompat.from(context);
        mNotifyMgr.notify(0, mBuilder.build());
    }

    public static void notifyReject(Context context, Pairing pairing, Request request, String title) {
        if (!new Settings(context).approvedNotificationsEnabled()) {
            return;
        }
        Intent resultIntent = new Intent(context, MainActivity.class);
        Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder mBuilder =
                buildNotification(context, APPROVED_CHANNEL_ID)
                        .setColor(Color.RED)
                        .setContentTitle(title)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (!new Settings(context).silenceNotifications()) {
            mBuilder.setSound(notificationSound)
                    .setVibrate(new long[]{0, 100, 100, 100});
        }
        request.body.visit(new RequestBody.Visitor<Void, RuntimeException>() {
            @Override
            public Void visit(MeRequest meRequest) throws RuntimeException {
                return null;
            }

            @Override
            public Void visit(SignRequest signRequest) throws RuntimeException {
                mBuilder.setContentText(pairing.workstationName + ": " + signRequest.display());
                return null;
            }

            @Override
            public Void visit(GitSignRequest gitSignRequest) throws RuntimeException {
                mBuilder
                        .setContentTitle(gitSignRequest.title() + " Rejected")
                        .setContentText(pairing.workstationName + ": " + gitSignRequest.display());

                RemoteViews remoteViewsSmall = new RemoteViews(context.getPackageName(), R.layout.result_remote);
                remoteViewsSmall.setTextViewText(R.id.workstationName, pairing.workstationName);
                remoteViewsSmall.setTextViewText(R.id.header, "Rejected Request From");
                gitSignRequest.fillShortRemoteViews(remoteViewsSmall, false, null);
                mBuilder.setCustomContentView(remoteViewsSmall);

                RemoteViews remoteViewsBig = new RemoteViews(context.getPackageName(), R.layout.result_remote);
                remoteViewsBig.setTextViewText(R.id.workstationName, pairing.workstationName);
                remoteViewsBig.setTextViewText(R.id.header, "Rejected Request From");
                gitSignRequest.fillRemoteViews(remoteViewsBig, false, null);
                mBuilder.setCustomBigContentView(remoteViewsBig);
                return null;
            }

            @Override
            public Void visit(UnpairRequest unpairRequest) throws RuntimeException {
                return null;
            }

            @Override
            public Void visit(HostsRequest hostsRequest) throws RuntimeException {
                return null;
            }
        });

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);

        NotificationManagerCompat mNotifyMgr = NotificationManagerCompat.from(context);
        mNotifyMgr.notify(0, mBuilder.build());
    }

    public static void requestApproval(Context context, Pairing pairing, Request request) {
        Intent approveOnceIntent = new Intent(context, UnlockScreenDummyActivity.class);
        approveOnceIntent.setAction(Policy.APPROVE_ONCE + "-" + request.requestID);
        approveOnceIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        approveOnceIntent.putExtra("action", Policy.APPROVE_ONCE);
        approveOnceIntent.putExtra("requestID", request.requestID);
        PendingIntent approveOncePendingIntent = PendingIntent.getActivity(context, (Policy.APPROVE_ONCE + "-" + request.requestID).hashCode(), approveOnceIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action.Builder approveOnceBuilder = new NotificationCompat.Action.Builder(
                R.drawable.ic_notification_checkmark, "Once", approveOncePendingIntent);

        Intent approveTemporarilyIntent = new Intent(context, UnlockScreenDummyActivity.class);
        approveTemporarilyIntent.setAction(Policy.APPROVE_THIS_TEMPORARILY + "-" + request.requestID);
        approveTemporarilyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        approveTemporarilyIntent.putExtra("action", Policy.APPROVE_THIS_TEMPORARILY);
        approveTemporarilyIntent.putExtra("requestID", request.requestID);
        PendingIntent approveTemporarilyPendingIntent = PendingIntent.getActivity(context, (Policy.APPROVE_THIS_TEMPORARILY + "-" + request.requestID).hashCode(), approveTemporarilyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action.Builder approveTemporarilyBuilder = new NotificationCompat.Action.Builder(
                R.drawable.ic_notification_stopwatch, "This host for " + Policy.temporaryApprovalDuration(), approveTemporarilyPendingIntent);

        Intent approveAllTemporarilyIntent = new Intent(context, UnlockScreenDummyActivity.class);
        approveAllTemporarilyIntent.setAction(Policy.APPROVE_ALL_TEMPORARILY + "-" + request.requestID);
        approveAllTemporarilyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        approveAllTemporarilyIntent.putExtra("action", Policy.APPROVE_ALL_TEMPORARILY);
        approveAllTemporarilyIntent.putExtra("requestID", request.requestID);
        PendingIntent approveAllTemporarilyPendingIntent = PendingIntent.getActivity(context, (Policy.APPROVE_ALL_TEMPORARILY + "-" + request.requestID).hashCode(), approveAllTemporarilyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action.Builder approveAllTemporarilyBuilder = new NotificationCompat.Action.Builder(
                R.drawable.ic_notification_stopwatch, "All for " + Policy.temporaryApprovalDuration(), approveAllTemporarilyPendingIntent);

        Intent rejectIntent = new Intent(context, NoAuthReceiver.class);
        rejectIntent.setAction(Policy.REJECT + "-" + request.requestID);
        rejectIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        rejectIntent.putExtra("action", Policy.REJECT);
        rejectIntent.putExtra("requestID", request.requestID);
        PendingIntent rejectPendingIntent = PendingIntent.getBroadcast(context, (Policy.REJECT + "-" + request.requestID).hashCode(), rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent clickIntent = new Intent(context, MainActivity.class);
        if (new OnboardingProgress(context).inProgress()) {
            clickIntent.setClass(context, OnboardingActivity.class);
        }
        clickIntent.setAction("CLICK-" + request.requestID);
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        clickIntent.putExtra("requestID", request.requestID);
        PendingIntent clickPendingIntent = PendingIntent.getActivity(context, ("CLICK-" + request.requestID).hashCode(), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder mBuilder =
                buildNotification(context, ACTION_REQUIRED_CHANNEL_ID)
                        .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .addAction(approveOnceBuilder.build())
                        .setDeleteIntent(rejectPendingIntent)
                        .setContentIntent(clickPendingIntent)
                        .setAutoCancel(true)
                ;
        request.body.visit(new RequestBody.Visitor<Void, RuntimeException>() {
            @Override
            public Void visit(MeRequest meRequest) throws RuntimeException {
                return null;
            }

            @Override
            public Void visit(SignRequest signRequest) throws RuntimeException {
                mBuilder.addAction(approveTemporarilyBuilder.build());
                mBuilder
                        .setContentTitle("Allow SSH Login?")
                        .setContentText(pairing.workstationName + ": " + signRequest.display());
                RemoteViews remoteViewsSmall = new RemoteViews(context.getPackageName(), R.layout.request_no_action_remote);
                remoteViewsSmall.setTextViewText(R.id.workstationName, pairing.workstationName);
                request.fillShortRemoteViews(remoteViewsSmall, null, null);
                mBuilder.setContent(remoteViewsSmall);

                RemoteViews remoteViewsBig = new RemoteViews(context.getPackageName(), R.layout.request_remote);
                remoteViewsBig.setTextViewText(R.id.workstationName, pairing.workstationName);
                request.fillRemoteViews(remoteViewsBig, null, null);
                remoteViewsBig.setOnClickPendingIntent(R.id.allowOnce, approveOncePendingIntent);
                remoteViewsBig.setTextViewText(R.id.allowAllTemporarily, "All for " + Policy.temporaryApprovalDuration());
                remoteViewsBig.setOnClickPendingIntent(R.id.allowAllTemporarily, approveAllTemporarilyPendingIntent);

                remoteViewsBig.setOnClickPendingIntent(R.id.allowTemporarily, approveTemporarilyPendingIntent);
                remoteViewsBig.setTextViewText(R.id.allowTemporarily, "This host for " + Policy.temporaryApprovalDuration());

                mBuilder.setCustomBigContentView(remoteViewsBig);
                return null;
            }

            @Override
            public Void visit(GitSignRequest gitSignRequest) throws RuntimeException {
                mBuilder.setContentTitle("Allow " + gitSignRequest.title() + "?")
                        .setContentText(pairing.workstationName + ": " + gitSignRequest.display());

                RemoteViews remoteViewsSmall = new RemoteViews(context.getPackageName(), R.layout.request_no_action_remote);
                remoteViewsSmall.setTextViewText(R.id.workstationName, pairing.workstationName);
                gitSignRequest.fillShortRemoteViews(remoteViewsSmall, null, null);
                mBuilder.setContent(remoteViewsSmall);

                RemoteViews remoteViewsBig = new RemoteViews(context.getPackageName(), R.layout.request_remote);
                remoteViewsBig.setTextViewText(R.id.workstationName, pairing.workstationName);
                gitSignRequest.fillRemoteViews(remoteViewsBig, null, null);
                remoteViewsBig.setOnClickPendingIntent(R.id.allowOnce, approveOncePendingIntent);
                remoteViewsBig.setTextViewText(R.id.allowAllTemporarily, "All for " + Policy.temporaryApprovalDuration());
                remoteViewsBig.setOnClickPendingIntent(R.id.allowAllTemporarily, approveAllTemporarilyPendingIntent);

                remoteViewsBig.setTextViewText(R.id.allowTemporarily, "");

                mBuilder.setCustomBigContentView(remoteViewsBig);
                return null;
            }

            @Override
            public Void visit(UnpairRequest unpairRequest) throws RuntimeException {
                return null;
            }

            @Override
            public Void visit(HostsRequest hostsRequest) throws RuntimeException {
                mBuilder.setContentTitle("Send user@hostname records?")
                        .setContentText(pairing.workstationName + " is requesting your SSH login records.");
                return null;
            }
        });
        if (!new Settings(context).silenceNotifications()) {
            mBuilder.setSound(notificationSound)
                    .setVibrate(new long[]{0, 100, 100, 100});
        }

        mBuilder.addAction(approveAllTemporarilyBuilder.build());

        NotificationManagerCompat mNotifyMgr = NotificationManagerCompat.from(context);
        mNotifyMgr.notify(request.requestID, 0, mBuilder.build());
    }

    public static void notifyPGPKeyExport(Context context, PGPPublicKey pubkey) {
        Intent clickIntent = new Intent(context, MainActivity.class);
        if (new OnboardingProgress(context).inProgress()) {
            clickIntent.setClass(context, OnboardingActivity.class);
        }
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent clickPendingIntent = PendingIntent.getActivity(context, ("CLICK-PGPPUBKEY").hashCode(), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        String userIDs = "";
        for (SignedPublicKeySelfCertification signedIdentity : pubkey.signedIdentities) {
            userIDs += signedIdentity.certification.userIDPacket.userID.toString() + "\n";
        }

        NotificationCompat.InboxStyle inboxStyle =
                new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle("Exported PGP Public Key");
        for (SignedPublicKeySelfCertification signedIdentity : pubkey.signedIdentities) {
            inboxStyle.addLine(signedIdentity.certification.userIDPacket.userID.toString());
        }

        NotificationCompat.Builder mBuilder =
                buildNotification(context, APPROVED_CHANNEL_ID)
                        .setContentTitle("Exported PGP Public Key")
                        .setContentText(userIDs.trim())
                        .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(clickPendingIntent)
                        .setAutoCancel(true)
                        .setStyle(inboxStyle)
                ;
        if (!new Settings(context).silenceNotifications()) {
            mBuilder.setSound(notificationSound)
                    .setVibrate(new long[]{0, 100, 100, 100});
        }

        NotificationManagerCompat mNotifyMgr = NotificationManagerCompat.from(context);
        mNotifyMgr.notify(1, mBuilder.build());
    }

    public static void clearRequest(Context context, Request request) {
        NotificationManagerCompat mNotifyMgr = NotificationManagerCompat.from(context);
        mNotifyMgr.cancel(request.requestID, 0);
    }
}
