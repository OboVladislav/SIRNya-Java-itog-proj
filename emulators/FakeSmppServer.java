import org.jsmpp.bean.BroadcastSm;
import org.jsmpp.bean.CancelBroadcastSm;
import org.jsmpp.bean.CancelSm;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.InterfaceVersion;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.QueryBroadcastSm;
import org.jsmpp.bean.QuerySm;
import org.jsmpp.bean.ReplaceSm;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.session.BindRequest;
import org.jsmpp.session.BroadcastSmResult;
import org.jsmpp.session.QueryBroadcastSmResult;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.Session;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.SMPPServerSessionListener;
import org.jsmpp.session.ServerMessageReceiverListener;
import org.jsmpp.session.SubmitMultiResult;
import org.jsmpp.session.SubmitSmResult;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.RandomMessageIDGenerator;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

/**
 * Минимальный фейковый SMPP-сервер (SMSC) для локального теста канала SMS без SMPPsim
 * и без реального оператора. Принимает любой bind и любой submit_sm, печатает SMS в консоль.
 * Использует jsmpp (уже в зависимостях проекта).
 * Запуск:  java -cp "target\otp-service.jar;." FakeSmppServer [port]   (порт по умолчанию 2775).
 */
public class FakeSmppServer {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 2775;
        try (SMPPServerSessionListener listener = new SMPPServerSessionListener(port)) {
            listener.setTimeout(0); // accept() блокируется до подключения
            System.out.println("Fake SMPP server listening on port " + port
                    + " (accepts any bind). SMS будут печататься здесь. Ctrl+C — стоп.");
            while (true) {
                SMPPServerSession session = listener.accept();
                new Thread(() -> serve(session)).start();
            }
        }
    }

    private static void serve(SMPPServerSession session) {
        try {
            session.setMessageReceiverListener(new PrintingListener());
            BindRequest bind = session.waitForBind(15000);
            System.out.println("Bind received: system_id=" + bind.getSystemId());
            bind.accept("FakeSMSC", InterfaceVersion.IF_34);
        } catch (Exception e) {
            System.err.println("SMPP session error: " + e.getMessage());
        }
    }

    /** Печатает входящие SMS; остальные операции SMPP не используются эмулятором. */
    private static class PrintingListener implements ServerMessageReceiverListener {

        private final MessageIDGenerator idGenerator = new RandomMessageIDGenerator();

        @Override
        public SubmitSmResult onAcceptSubmitSm(SubmitSm submitSm, SMPPServerSession source) {
            String text = new String(submitSm.getShortMessage(), StandardCharsets.UTF_8);
            System.out.println("\n===== SMS RECEIVED @ " + LocalTime.now() + " =====");
            System.out.println("to:   " + submitSm.getDestAddress());
            System.out.println("from: " + submitSm.getSourceAddr());
            System.out.println("text: " + text);
            System.out.println("=============================================\n");
            return new SubmitSmResult(idGenerator.newMessageId(), new OptionalParameter[0]);
        }

        @Override
        public SubmitMultiResult onAcceptSubmitMulti(SubmitMulti submitMulti, SMPPServerSession source) {
            return null;
        }

        @Override
        public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) {
            return null;
        }

        @Override
        public QuerySmResult onAcceptQuerySm(QuerySm querySm, SMPPServerSession source) {
            return null;
        }

        @Override
        public void onAcceptReplaceSm(ReplaceSm replaceSm, SMPPServerSession source) {
        }

        @Override
        public void onAcceptCancelSm(CancelSm cancelSm, SMPPServerSession source) {
        }

        @Override
        public BroadcastSmResult onAcceptBroadcastSm(BroadcastSm broadcastSm, SMPPServerSession source) {
            return null;
        }

        @Override
        public void onAcceptCancelBroadcastSm(CancelBroadcastSm cancelBroadcastSm, SMPPServerSession source) {
        }

        @Override
        public QueryBroadcastSmResult onAcceptQueryBroadcastSm(QueryBroadcastSm queryBroadcastSm, SMPPServerSession source) {
            return null;
        }
    }
}
