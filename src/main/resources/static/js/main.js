/** Entry point: initialize each owner once, then start live data reads. */
import {init as navigation} from './ui/navigation.js';
import {init as dialogs} from './ui/dialogs.js';
import {init as paymentForm} from './views/paymentForm.js';
import {init as payments} from './views/payments.js';
import {init as transactions} from './views/transactions.js';
import {init as accounts} from './views/accounts.js';
import {init as mesh} from './views/mesh.js';
import {init as activity} from './views/activity.js';
import {init as analytics} from './views/analytics.js';
import {init as security} from './views/security.js';
import {init as search} from './services/search.js';
import {renderIcons} from './ui/dom.js';
import {start} from './services/runtime.js';
[navigation,dialogs,paymentForm,payments,transactions,accounts,mesh,activity,analytics,security,search].forEach(init=>init());
renderIcons();
start();
