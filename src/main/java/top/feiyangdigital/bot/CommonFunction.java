package top.feiyangdigital.bot;

import com.alibaba.fastjson2.JSONObject;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.SafeSearchAnnotation;
import com.unfbx.chatgpt.entity.chat.ChatChoice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.callBack.deleteRuleCallBack.DeleteSingleRuleByKeyWord;
import top.feiyangdigital.callBack.replyRuleCallBack.AddAutoReplyRule;
import top.feiyangdigital.entity.BaseInfo;
import top.feiyangdigital.entity.BotRecord;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.handleService.*;
import top.feiyangdigital.sqlService.BotRecordService;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.*;
import top.feiyangdigital.utils.aiMessageCheck.AiCheckMedia;
import top.feiyangdigital.utils.aiMessageCheck.AiCheckMessage;
import top.feiyangdigital.utils.groupCaptch.CaptchaManager;
import top.feiyangdigital.utils.groupCaptch.RestrictOrUnrestrictUser;
import top.feiyangdigital.utils.ruleCacheMap.AddRuleCacheMap;
import top.feiyangdigital.utils.ruleCacheMap.DeleteRuleCacheMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class CommonFunction {

    @Autowired
    private AiCheckMessage aiCheckMessage;

    @Autowired
    private AiCheckMedia aiCheckMedia;

    @Autowired
    private CheckUser checkUser;

    @Autowired
    private TimerDelete timerDelete;

    @Autowired
    private NewMemberIntoGroup newMemberIntoGroup;

    @Autowired
    private BotHelper botHelper;

    @Autowired
    private AdminList adminList;

    @Autowired
    private GroupInfoService groupInfoService;

    @Autowired
    private SendContent sendContent;

    @Autowired
    private AddRuleCacheMap addRuleCacheMap;

    @Autowired
    private DeleteRuleCacheMap deleteRuleCacheMap;

    @Autowired
    private AddAutoReplyRule addAutoReplyRule;

    @Autowired
    private DeleteSingleRuleByKeyWord deleteSingleRuleByKeyWord;

    @Autowired
    private CaptchaGenerator captchaGenerator;

    @Autowired
    private CaptchaManager captchaManager;

    @Autowired
    private BotFirstIntoGroup botFirstIntoGroup;

    public void mainFunc(AbsSender sender, Update update) {


        if (update.hasMessage() && (update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat())) {
            if (("/setbot".equals(update.getMessage().getText()) || ("/setbot@" + BaseInfo.getBotName()).equals(update.getMessage().getText())) && ("GroupAnonymousBot".equals(update.getMessage().getFrom().getUserName()) || checkUser.isChatOwner(sender, update))) {
                GroupInfoWithBLOBs groupInfo = new GroupInfoWithBLOBs();
                groupInfo.setOwnerandanonymousadmins(adminList.fetchHighAdminList(sender, update));
                groupInfo.setGroupname(update.getMessage().getChat().getTitle());
                groupInfo.setSettingtimestamp(String.valueOf(new Date().getTime()));
                groupInfoService.updateSelectiveByChatId(groupInfo, update.getMessage().getChatId().toString());
                botHelper.sendAdminButton(sender, update);
                return;
            } else if ("/setbot".equals(update.getMessage().getText()) || ("/setbot@" + BaseInfo.getBotName()).equals(update.getMessage().getText())) {
                timerDelete.sendTimedMessage(sender, sendContent.messageText(update, "你没有权限执行此命令"), 10);
                return;
            }
            if (update.getMessage().hasText()) {
                aiCheckMessage.checkMessage(sender, update);
                return;
            }
            aiCheckMedia.checkMedia(sender, update);
        }

        if (update.hasMessage() && update.getMessage().getText() != null && update.getMessage().getChat().isUserChat()) {

            if (update.getMessage().getText().contains("start _groupId")) {
                GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(update.getMessage().getText().split("_")[1].substring(7));
                if (groupInfoWithBLOBs.getOwnerandanonymousadmins().contains(update.getMessage().getFrom().getId().toString())) {
                    addRuleCacheMap.updateUserMapping(update.getMessage().getFrom().getId().toString(), groupInfoWithBLOBs.getGroupid(), groupInfoWithBLOBs.getGroupname(), groupInfoWithBLOBs.getKeywordsflag(), groupInfoWithBLOBs.getAiflag());
                    deleteRuleCacheMap.updateUserMapping(update.getMessage().getFrom().getId().toString(), groupInfoWithBLOBs.getGroupid(), groupInfoWithBLOBs.getGroupname(), groupInfoWithBLOBs.getDeletekeywordflag());
                    botHelper.sendInlineKeyboard(sender, update);
                }
            } else if (update.getMessage().getText().contains("start _intoGroupInfo")) {
                String[] idGroup = update.getMessage().getText().split("_")[1].substring(13).split("and");
                String chatId = idGroup[0];
                String userId = idGroup[1];
                String currentChatId = update.getMessage().getChatId().toString();
                String firstName = update.getMessage().getChat().getFirstName();
                if (update.getMessage().getFrom().getId().toString().equals(userId) && "open".equals(groupInfoService.selAllByGroupId(chatId).getIntogroupcheckflag())) {

                    captchaGenerator.sendCaptcha(sender, update.getMessage().getFrom().getId(), chatId, currentChatId, firstName);
                } else {
                    try {
                        sender.execute(sendContent.messageText(update, "❌这不是你的验证"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if ("/start".equals(update.getMessage().getText())) {
                String url = String.format("https://t.me/%s?startgroup=start", BaseInfo.getBotName());
                List<String> keywordsButtons = new ArrayList<>();
                KeywordsFormat keywordsFormat = new KeywordsFormat();
                keywordsButtons.add("➕将Bot加入群组$$" + url);
                keywordsButtons.add("❌关闭菜单##close");
                keywordsFormat.setReplyText("<b>GitHub地址：</b><b><a href='https://github.com/youshandefeiyang/feiyangdigital-bot'>点击查看</a></b>\n<b>官方群组：</b><b><a href='https://t.me/feiyangdigital'>点击加入</a></b>\n");
                keywordsFormat.setKeywordsButtons(keywordsButtons);
                try {
                    sender.execute(sendContent.createResponseMessage(update, keywordsFormat, "html"));
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            } else {
                String userId = update.getMessage().getFrom().getId().toString();
                if ("allow".equals(addRuleCacheMap.getKeywordsFlagForUser(userId))) {
                    addAutoReplyRule.addNewRule(sender, update);
                } else if ("candelete".equals(deleteRuleCacheMap.getDeleteKeywordFlagMap(userId))) {
                    deleteSingleRuleByKeyWord.DeleteOption(sender, update);
                } else if (StringUtils.hasText(captchaManager.getAnswerForUser(userId))) {
                    captchaGenerator.answerReplyhandle(sender, update);

                }
            }

        }


        //检测新入群用户且状态正常的用户
        if (update.getChatMember() != null && "left".equalsIgnoreCase(update.getChatMember().getOldChatMember().getStatus()) && "member".equalsIgnoreCase(update.getChatMember().getNewChatMember().getStatus())) {

            newMemberIntoGroup.handleMessage(sender, update, null);
        }

        //检测新入群Bot
        if (update.hasMessage() && (update.getMessage().getNewChatMembers() != null && !update.getMessage().getNewChatMembers().isEmpty())) {

            botFirstIntoGroup.handleMessage(sender, update);
        }

        if (update.hasCallbackQuery()) {
            botHelper.handleCallbackQuery(sender, update);
        }
    }

}
