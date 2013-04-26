define({
	root: ({
			copyright: "(C) 2012-2013 IBM Corporation and Others. All Rights Reserved",
			loading: "loading",
			loading2: "loading.",
			loading3: "loading..",
			
			loadingMsg_desc: "Current loading status",
			loading_reloading: "Force Reloading Page",
			loading_retrying: "Retrying",
			loading_nocontent: "This locale cannot be displayed.",
			loadingOneRow: "loading....",
			voting: "Voting",
			checking: "Checking",
			
			itemCount: "Items: ${itemCount}",
			itemCountHidden: "Items shown: ${itemCount}; Items hidden at ${coverage} coverage level: ${skippedDueToCoverage}",
			itemCountAllHidden: "No items visible due to coverage level.",
			itemCountNone: "No items!",
			noVotingInfo: " (no voting info received)",
			newDataWaiting: "(new data waiting)",

			clickToCopy: "click to copy to input box",
			file_a_ticket: "File a ticket...",
			file_ticket_unofficial: "This is not an official Survey Tool instance.",
			file_ticket_must: "You must file a ticket to modify this item.",

			htmlst: "Errors",
			htmldraft: "A",
			htmlvoted: "Voting",
			htmlcode: "Code",
			htmlbaseline: "$BASELINE_LANGUAGE_NAME",
			htmlproposed: "Proposed",
			htmlothers: "Others",
			htmlchange: "Change",
			htmlnoopinion: "Abstain",
			
			possibleProblems: "Possible problems with this locale:",

			flyoverst: "Status Icon",
			flyoverdraft: "Approval Status",
			flyovervoted: "Shows a checkmark if you voted",
			flyovercode: "Code for this item",
			extraAttribute_desc: "Additional specifiers for this element",
			extraAttribute_heading: "Note: there are additional specifiers for this element. Read the help page for further details.",
			flyoverbaseline: "Comparison value",
			flyoverproposed: "Winning value",
			flyoverothers: "Other non-winning items",
			flyoverchange: "Enter new values here",
			flyovernoopinion: "Abstain from voting on this row",
			
			itemInfoBlank: "This area shows further details about the selected item.",
			
			draftStatus: "Status: ${0}",
			confirmed: "Confirmed", 
			approved: "Approved", 
			unconfirmed: "Unconfirmed", 
			contributed: "Contributed", 
			provisional: "Provisional",
			missing: "Missing",
			
			
			admin_settings: "Settings",
			admin_settings_desc: "Survey tool settings",
			adminSettingsChangeTemp: "Temporary change:",
			appendInputBoxChange: "Change",
			appendInputBoxCancel: "Clear",
			
			userlevel_admin: "Admin",
			userlevel_tc: "TC",
			userlevel_expert: "Expert",
			userlevel_vetter: "Vetter",
			userlevel_street: "Guest",
			userlevel_locked: "Locked",
			userlevel_manager: "Manager",

			userlevel_admin_desc: "Administrator",
			userlevel_tc_desc: "CLDR-Technical Committee member",
			userlevel_expert_desc: "Language Expert",
			userlevel_vetter_desc: "Regular Vetter",
			userlevel_street_desc: "Guest User",
			userlevel_manager_desc: "Project Manager",
			userlevel_locked_desc: "Locked User, no login",
			
			admin_threads: "Threads",
			admin_threads_desc: "All Threads",
			adminClickToViewThreads: "Click a thread to view its call stack",

			admin_exceptions: "Exception Log",
			admin_exceptions_desc: "Contents of the exceptions.log",
			adminClickToViewExceptions: "Click an exception to view its call stack",
			
			adminExceptionSQL_desc: "SQL state and code",
			adminExceptionSTACK_desc: "Exception call stack",
			adminExceptionMESSAGE_desc: "Exception message",
			adminExceptionUptime_desc: "ST uptime at stack time",
			adminExceptionHeader_desc: "Overall error message and cause",
			adminExceptionLogsite_desc: "Location of logException call",
			adminExceptionDup: "(${0} other time(s))",
			last_exception: "(last exception)",
			more_exceptions: "(more exceptions...)",
			no_exceptions: "(no exceptions.)",
			adminExceptionDupList: "List of other instances:",
			clickToSelect: "select",
			
			admin_ops: "Actions",
			admin_ops_desc: "Administrative Actions",
			
			notselected_desc: '',
			
			recentLoc: "Locale",
			recentXpath: "XPath",
			recentValue: "Value",
			recentWhen: "When",
			recentOrg: "Organization",
			recentNone: "No items to show.",
                        recentCount: "Count",
                        downloadXmlLink: "Download XML...",

			testOkay: "has no errors or warnings",
			testWarn: "has warnings",
			testError: "has errors",
			
			voTrue: "You have already voted on this item.",
			voFalse: "You have not yet voted on this item.",

			online: "Online",
			disconnected: "Disconnected",
			error_restart: "(May be due to SurveyTool restart on server)",
			error: "Disconnected: Error",
			details: "Details...",
			startup: "Starting up...",
			
			admin_users: "Users",
			admin_users_desc: "Currently logged-in users",
                        
                        // pClass ( see DataSection.java)
                        pClass_winner: "This item is currently winning.",
                        pClass_alias: "This item is aliased from another location.",
                        pClass_fallback_code: "This item is an untranslated code.",
                        pClass_fallback_root: "This item is inherited from the root locale.",
                        pClass_loser: "This is a proposed item which is not currently winning.",
                        pClass_fallback: "This item is inherited from ${inheritFromDisplay}.",
                        pClassExplain_desc: "This area shows the item's status.",
			
           winningStatus_disputed: "Disputed",
           winningStatus_msg:  "${1} ${0} Value ",
           lastReleaseStatus_msg: "${0} Last Release Value ",
           lastReleaseStatus1_msg: "",
           
           htmlvorg: "Org",
           htmlvorgvote: "Organization's vote",
           htmlvdissenting: "Dissenting Votes",	   
           flyovervorg: "List of Organizations",
           flyovervorgvote: "The final vote for this organization",
           flyovervdissenting: "Other votes cast against the final vote by members of the organization",
           voteInfoScorebox_msg: "${0}: ${1}",
           voteInfo_established_url: "http://cldr.unicode.org/index/process#TOC-Draft-Status-of-Optimal-Field-Value",
           voteInfo_established: "This is an established locale.",
           voteInfo_orgColumn: "Org.",
           voteInfo_noVotes: "(no votes)",
           voteInfo_iconBar_desc: "This area shows the status of each candidate item.",
           voteInfo_noVotes_desc: "There were no votes for this item.",
           voteInfo_key: "Key:",
           voteInfo_valueTitle_desc: "Item's value",
           voteInfo_orgColumn_desc: "Which organization is voting",
           voteInfo_voteTitle_desc: "The total vote score for this value",
           voteInfo_orgsVote_desc: "This vote is the organization's winning vote",
           voteInfo_orgsNonVote_desc: "This vote is not the organization's winning vote",
           voteInfo_lastRelease_desc: "This mark shows on the item which was approved in the last release, if any.",
           voteInfo_lastReleaseKey_desc: "This mark shows on the item which was approved in the last release, if any.",
           voteInfo_winningItem_desc: "This mark shows the item which is currently winning.",
           voteInfo_winningKey_desc: "This mark shows the item which is currently winning.",
           voteInfo_perValue_desc: "This shows the state and voters for a particular item.",
           // CheckCLDR.StatusAction 
           StatusAction_msg:              "Item was not submitted: ${0}",
           StatusAction_ALLOW:            "(Actually, it was allowed.)", // shouldn't happen
           StatusAction_FORBID:           "Forbidden.",
           StatusAction_FORBID_ERRORS:    "Item had errors.",
           StatusAction_FORBID_READONLY:  "Read-only.",
           StatusAction_FORBID_COVERAGE:  "Outside of coverage.",
           
           // v.jsp
           "v-title2_desc": "Locale title",
           v_bad_special_msg:  "Bad URL (mistyped?), unknown special action: \"${special}\"",
           v_oldvotes_title: "Old Votes - from before ${votesafter}",
           v_oldvotes_count_msg: "Uncontested Vote Count: ${uncontested}, Contested Vote Count: ${contested}",
           v_oldvotes_title_uncontested: "Uncontested Votes",
           v_oldvotes_desc_uncontested_msg: "These are your votes which agreed with the winning  value in previous CLDR ${version}.",
           v_oldvotes_title_contested: "Contested Votes",
           v_oldvotes_desc_contested_msg: "These are your votes which did not agree with the winning value in previous CLDR ${version}. You may choose to accept them if you believe they still represent the best value.",
           v_oldvotes_locale_list_help_msg: "Here is a list of locales which you have voted for in CLDR ${version} and previous. Click one to accept old votes from the previous CLDR version.",
           v_oldvotes_return_to_locale_list: "Return to List of Locales with old votes",
           v_oldvotes_path: "Path",
           v_oldvotes_locale_msg: "These are your contested and uncontested votes for CLDR ${version} in ${locale}. Expand the section you want to work with, choose items by click, and submit votes. Just ignore any items you don't want to vote for.",
           "v-oldvotes-loc-help_desc": "Specific help on this locale's old votes",
           "v-oldvotes-desc_desc": "Specific help on this type of vote",
           "v-accept_desc": "Checked items will be accepted, unchecked items will not be accepted.",
           "code_desc": "The short code for this item. ",
           "v-path_desc": "The short code for this item. Click here to view the item, in a new window.",
           "v-comp_desc": "The comparison value (English)",
           "v-win_desc": "This was the winning value for previous CLDR",
           "v-mine_desc": "This was your vote for the previous CLDR",
           "pathChunk_desc": "This header separates common items",
           v_oldvotes_winning_msg: "CLDR ${version} winning",
           v_oldvotes_mine: "My old vote",
           v_oldvotes_summary: "Summary",
           v_oldvotes_accept: "Accept?",
           v_oldvotes_all: "Choose All",
           v_oldvotes_go: "view",
           v_oldvotes_none: "Choose None",
           v_oldvotes_no_contested: "No contested votes.",
           v_oldvotes_no_old_here: "No old votes to import. You're done with this locale!",
           v_oldvotes_no_old: "No old votes to import. You're done with old votes!!",
           v_submit_msg: "Vote for selected ${type}",
           v_submit_busy: "Submitting...",
           
           v_oldvote_remind_msg: "CLDR: Old Votes Reminder Message",
           v_oldvote_remind_desc_msg: "You currently have ${count} votes from previous CLDR vetting periods. Would you like to view them for import into the current release?<p>  (Note: You can always import these votes via the '<span class=notselected>Manage</span>' link once logged in.)",
           v_oldvote_remind_yes: "Yes, view old votes",
           v_oldvote_remind_no: "No, not today",
           v_oldvote_remind_dontask: "No, and don't ask again",
           "v-title_desc": "This area shows the date before which votes are considered “old”.",
           special_oldvotes: "Import Old Votes",
           section_general: "General Info",
           section_forum: "Forum <i>(Leaves this page)</i>",
           forumNewPostButton: "New Post (leaves this page)",
           forumNewButton_desc: "Clicking this will bring up a form to reply to this particular item, however it does (currently) leave the page you are on. Click 'view item' after submitting to return to this item.",
           special_general: "Please click the <b class='fakebutton'>General Info</b> button above, and choose a page to begin entering data. If you have not already done so, please read <a href='http://www.unicode.org/cldr/survey_tool.html'>Instructions</a>, particularly the Guide and the Walkthrough.",

           defaultContent_msg: "This locale, ${info.name}  is the <i><a target='CLDR-ST-DOCS' href='http://cldr.unicode.org/translation/default-content'>default content locale</a></i> for <b><a class='notselected' href='#/${info.dcParent}'>${dcParentName}</a></b>, and thus editing or viewing is disabled. ",
           defaultContentChild_msg: "This locale, ${info.name}  supplies the <i><a target='CLDR-ST-DOCS' href='http://cldr.unicode.org/translation/default-content'>default content</a></i> for <b><a class='notselected' href='#/${info.dcChild}'>${dcChildName}</a></b>. Please make sure that all the changes that you make here are appropriate for <b>${dcChildName}</b>. If you add any changes that are inappropriate for other sublocales, be sure to override their values.",           
           defaultContent_header_msg: "= ${dcChild}",
           defaultContent_titleLink: "content",
           readonly_msg: "This locale may not be edited.<br/> ${msg}",
           readonly_unknown: "Reason: Administrative Policy.",
           
           ari_message: 'Uh-oh! Not able to successfully communicate with the SurveyTool server.',
           ari_force_reload: '[Second try: will force page reload]',
           
           coverage_auto_msg: 'Automatic (Currently: ${surveyOrgCov})',
           coverage_core: 'Core',
           coverage_posix: 'POSIX',
           coverage_minimal: 'Minimal',
           coverage_basic: 'Basic',
           coverage_moderate: 'Moderate',
           coverage_modern: 'Modern',
           coverage_comprehensive: 'Comprehensive',
           coverage_optional: 'Optional',
           
           coverage_menu_desc: 'Change the displayed coverage level. "Automatic" will use your organization\'s preferred value for this locale, if any.',
           
           jsonStatus_msg: "You should see your content shortly, thank you for waiting. By the way, there are ${users} logged-in users and ${guests} visitors to the SurveyTool. The server's workload is about ${sysloadpct} of normal capacity. You have been waiting about ${waitTime} seconds.",
           
           
           "": ""})
//		"mt-MT": false
	
  // sublocales
});